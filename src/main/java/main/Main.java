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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class Main {
    private static final String PROJECT_NAME = "SYNCOPE";

    private static List<GitCommit> retrieveCommitsWithJiraTickets(List<JiraTicket> tickets, Date maxDate) throws GitAPIException {
        List<GitCommit> commits = new ArrayList<>();

        for (JiraTicket ticket : tickets) {
            GitAnalyzer analyzer = new GitAnalyzer();
            // get the entire log of commits
            Iterable<RevCommit> gitLog = analyzer.getGitLog(GitSingleton.getInstance().getGit(), maxDate);
            // here we pass the ticket as a keyword to be searched in commit message
            ArrayList<RevCommit> results = new ArrayList<>(analyzer.getCommitsContainingString(gitLog, ticket.getKey()));

            GitCommitFactory factory = GitCommitFactory.getInstance();
            for (RevCommit commit : results) {
                commits.add(factory.parseCommit(commit, ticket.getKey()));
            }
        }
        /*for (GitCommit commit : commits){
         logger.log(Level.INFO, commit.toString());
         }*/
        commits.sort((a,b) -> {
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
        Logger logger = LoggerSingleton.getInstance().getLogger();


        /*--------------------------------------------------------JIRA (RELEASES + TICKETS)-------------------------------------------------------*/

        /*
        The constructor of the VersionManager retrieves all the released version for the specified project,
        store them sorted chronologically, both all versions and the first half of them.
         */
        VersionManager versionManager = new VersionManager(PROJECT_NAME, logger);
        versionManager.setReleases();
        Date maxDate = versionManager.getLatestReleaseDate();
        /*
         * Now, let's interact with Jira again to retrieve tickets of all fixed bugs
         * */
        ArrayList<JiraTicket> tickets = (ArrayList<JiraTicket>) RetrieveTicketsID.getTicketsID(PROJECT_NAME);
        /*for (JiraTicket ticket : tickets)
            logger.info(ticket.toString());
        */

        /*---------------------------------------------------------------------GIT-------------------------------------------------------------*/
        /*
         * Now, for each ticket, let see in which Git commit is present
         * */

        List<GitCommit> fixCommits = retrieveCommitsWithJiraTickets(tickets, maxDate);
        List<RevCommit> allCommits = new GitAnalyzer().getDatetimeSortedGitLog(GitSingleton.getInstance().getGit(), maxDate);


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
         *  - if Jira fix date corresponds to commit date, then that commit is the fix commit;
         *  - otherwise, the latest commit referring that bug is chosen as FixCommit
         *
         * Bugs with no such commits are removed from the list.
         */

        BugManager.patchFixCommit(bugs);

        bugs = versionManager.calculateVersionsForBugs(bugs);
        logger.info("Versions for bug calculated");

        Map<String, List<RevCommit>> commitPerRelease = versionManager.splitCommitsPerRelease(allCommits);
        logger.info("Commit split ovr releases. DONE");

        /*-----------------------------------------------GIT FILES------------------------------------------------------*/

        /*
        git diff --stat -M --name-status <commitID>
        */

        GitManager gitManager = new GitManager(GitSingleton.getInstance().getGit());
        DatasetCreator datasetCreator = new DatasetCreator(versionManager, gitManager, bugs, logger);

        logger.info("Dataset creation begins ...");
        List<DatasetInstance> dataset = datasetCreator.computeDataset(commitPerRelease, fixCommits);

        List<String[]> arrayOfCSVEntry = new ArrayList<>();
        // add headings
        arrayOfCSVEntry.add(new String[]{"Version", "Filename", "Buggy"});
        for (DatasetInstance entry : dataset){
            arrayOfCSVEntry.add(entry.toStringArray());
        }

        CSVManager.csvWriteAll("Syncope_dataset.csv", arrayOfCSVEntry);
    }





}
