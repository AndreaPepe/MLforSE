package main;

import controller.BugManager;
import controller.DatasetCreator;
import controller.GitManager;
import controller.VersionManager;
import csv.CSVManager;
import git.GitAnalyzer;
import git.GitCommitFactory;
import git.GitSingleton;
import jira.RetrieveTicketsID;
import logging.LoggerSingleton;
import model.Bug;
import model.DatasetInstance;
import model.GitCommit;
import model.JiraTicket;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

public class Main {

    private static List<GitCommit> retrieveCommitsWithJiraTickets(List<JiraTicket> tickets, Date maxDate) throws GitAPIException {
        List<GitCommit> commits = new ArrayList<>();
        List<RevCommit> revCommits = new ArrayList<>();

        for (JiraTicket ticket : tickets) {
            GitAnalyzer analyzer = new GitAnalyzer();
            // get the entire log of commits
            Iterable<RevCommit> gitLog = analyzer.getGitLog(GitSingleton.getInstance().getGit(), maxDate);
            // here we pass the ticket as a keyword to be searched in commit message
            ArrayList<RevCommit> results = new ArrayList<>(analyzer.getCommitsContainingString(gitLog, ticket.getKey()));

            GitCommitFactory factory = GitCommitFactory.getInstance();
            for (RevCommit commit : results) {
                if(! revCommits.contains(commit)) {
                    // avoid duplicates (commits that refer more than 1 Jira ticket)
                    // it's maintained only the reference to a single Jira ticket for simplicity
                    revCommits.add(commit);
                    commits.add(factory.parseCommit(commit, ticket.getKey()));
                }
            }
        }
        commits.sort((a, b) -> {
            int aCommitTime = a.getRevCommit().getCommitTime();
            int bCommitTime = b.getRevCommit().getCommitTime();
            if (aCommitTime > bCommitTime)
                return 1;
            else if (aCommitTime == bCommitTime)
                return 0;
            return -1;
        });
        return commits;
    }

    public static void main(String[] args) throws Exception {
        InputStream resource = Main.class.getClassLoader().getResourceAsStream("config.json");
        String projectName;
        if (resource != null) {
            BufferedReader config = new BufferedReader(new InputStreamReader(resource));
            JSONObject obj = (JSONObject) new JSONParser().parse(config);
            String result = (String) obj.get("repo");
            String[] strings = result.split("\\\\");
            projectName = strings[strings.length - 1].toUpperCase(Locale.ROOT);
        } else {
            throw new IllegalArgumentException("Project name not found");
        }
        Logger logger = LoggerSingleton.getInstance().getLogger();


        /*--------------------------------------------------------JIRA (RELEASES + TICKETS)-------------------------------------------------------*/

        /*
        The constructor of the VersionManager retrieves all the released version for the specified project,
        store them sorted chronologically, both all versions and the first half of them.
         */
        logger.info("Retrieving project's releases from Jira ...");
        VersionManager versionManager = new VersionManager(projectName, logger);
        versionManager.setReleases();
        Date maxDate = versionManager.getLatestReleaseDate();
        logger.info(String.format("Retrieved releases: %d\tLatest release date: %s", versionManager.getReleasesSize(), maxDate.toString()));
        /*
         * Now, let's interact with Jira again to retrieve tickets of all fixed bugs
         * */
        ArrayList<JiraTicket> tickets = (ArrayList<JiraTicket>) RetrieveTicketsID.getTicketsID(projectName);
        logger.info(String.format("Jira tickets: %d", tickets.size()));

        /*---------------------------------------------------------------------GIT-------------------------------------------------------------*/
        /*
         * Now, for each ticket, let see in which Git commit is present
         * */

        logger.info("\nRetrieving commits from Git ...");
        List<GitCommit> fixCommits = retrieveCommitsWithJiraTickets(tickets, maxDate);
        List<RevCommit> allCommits = new GitAnalyzer().getDatetimeSortedGitLog(GitSingleton.getInstance().getGit(), maxDate);
        logger.info(String.format("Total commits: %d", allCommits.size()));
        logger.info(String.format("Fix commits: %d", fixCommits.size()));

        /*---------------------------------------------------------------------BUGS----------------------------------------------------------*/

        // Let's create a list of bugs objects from the jira tickets and then refine them with consistency checks
        List<Bug> bugs = new ArrayList<>();
        for (JiraTicket ticket : tickets) {
            bugs.add(new Bug(ticket));
        }

        // let's process bugs with commits to define fix commit and other commits for the specific bug
        BugManager.setFixCommitAndOtherCommits(bugs, fixCommits);

        /* now, remove bugs with no bounded commit and add metadata on which is its fixing commit
         * That can be chosen in 2 ways:
         *  - if Jira fix date corresponds to commit date, then that commit is the fix commit
         *  - otherwise, the latest commit referring that bug is chosen as FixCommit
         *
         * Bugs with no such commits are removed from the list.
         */

        BugManager.patchFixCommit(bugs);

        bugs = versionManager.calculateVersionsForBugs(bugs);
        logger.info("\nIdentification of FV, OV, AVs and IV for bugs. DONE");

        Map<String, List<RevCommit>> commitPerRelease = versionManager.splitCommitsPerRelease(allCommits);
        logger.info("\nSplit commits by releases. DONE");

        /*-----------------------------------------------GIT FILES------------------------------------------------------*/

        /*
        git diff --stat -M --name-status <commitID>
        */

        GitManager gitManager = new GitManager(GitSingleton.getInstance().getGit());
        DatasetCreator datasetCreator = new DatasetCreator(versionManager, gitManager, bugs, logger);

        logger.info("\nDataset creation begins ...\n");
        List<DatasetInstance> dataset = datasetCreator.computeDataset(commitPerRelease);

        int numBuggy = 0;
        List<String[]> newSet = new ArrayList<>();
        int numDuplicates = 0;
        for(DatasetInstance instance : dataset){
            //check for duplicates
            String[] newEntry = new String[]{instance.getVersion(), instance.getFilename()};
            if(! newSet.contains(newEntry))
                newSet.add(new String[]{instance.getVersion(), instance.getFilename()});
            else {
                numDuplicates++;
            }
            // get number of buggy instances
            if(instance.isBuggy()){
                numBuggy++;
            }
        }
        logger.info(String.format("\nDataset size: %d instances", dataset.size()));
        logger.info(String.format("Buggy instances: %d", numBuggy));
        logger.info(String.format("Buggy percentage: %f %%", ((float)numBuggy/ dataset.size())*100.0));
        logger.info(String.format("Number of duplicated instances: %d", numDuplicates));
        List<String[]> arrayOfCSVEntry = new ArrayList<>();
        // add headings
        arrayOfCSVEntry.add(new String[]{
                "Release",
                "Filename",
                "Size",
                "LOC touched",
                "LOC added",
                "Max LOC added",
                "Avg LOC added",
                "Number of revisions",
                "Number of fixed bugs",
                "Number of authors",
                "Churn",
                "Max churn",
                "Avg churn",
                "Age",
                "Buggy"});

        for (DatasetInstance entry : dataset) {
            arrayOfCSVEntry.add(entry.toStringArray());
        }

        CSVManager.csvWriteAll(projectName.toLowerCase(Locale.ROOT) + "_dataset.csv", arrayOfCSVEntry);
    }


}
