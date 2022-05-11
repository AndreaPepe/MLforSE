package main;
/*
 * To run the following script use this JVM command line configuration
 * in order to avoid Weka exceptions in the log:
 *
 * java --add-opens java.base/java.lang=ALL-UNNAMED
 *
 */

import controller.*;
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
import weka.ClassifierEvaluation;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
                if (!revCommits.contains(commit)) {
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
        String log;
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

        log = String.format("Retrieved releases: %d\tLatest release date: %s", versionManager.getReleasesSize(), maxDate.toString());
        logger.info(log);
        /*
         * Now, let's interact with Jira again to retrieve tickets of all fixed bugs
         * */
        ArrayList<JiraTicket> tickets = (ArrayList<JiraTicket>) RetrieveTicketsID.getTicketsID(projectName);
        log = String.format("Jira tickets: %d", tickets.size());
        logger.info(log);

        /*---------------------------------------------------------------------GIT-------------------------------------------------------------*/
        /*
         * Now, for each ticket, let see in which Git commit is present
         * */

        logger.info("\nRetrieving commits from Git ...");
        List<GitCommit> fixCommits = retrieveCommitsWithJiraTickets(tickets, maxDate);
        List<RevCommit> allCommits = new GitAnalyzer().getDatetimeSortedGitLog(GitSingleton.getInstance().getGit(), maxDate);

        log = String.format("Total commits: %d", allCommits.size());
        logger.info(log);
        log = String.format("Fix commits: %d", fixCommits.size());
        logger.info(log);

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
        logger.info("\nDataset creation. DONE");

        int numBuggy = 0;
        List<String[]> newSet = new ArrayList<>();
        List<String[]> toAdd = new ArrayList<>();
        int numDuplicates = 0;
        for (DatasetInstance instance : dataset) {
            //check for duplicates
            String[] newEntry = new String[]{instance.getVersion(), instance.getFilename()};
            for (String[] arrayEntry : newSet) {
                if (arrayEntry[0].equals(newEntry[0]) && arrayEntry[1].equals(newEntry[1])) {
                    numDuplicates++;
                } else {
                    toAdd.add(new String[]{instance.getVersion(), instance.getFilename()});
                }
            }
            newSet.addAll(toAdd);
            toAdd = new ArrayList<>();

            // get number of buggy instances
            if (instance.isBuggy()) {
                numBuggy++;
            }
        }

        log = String.format("\nDataset size: %d instances", dataset.size());
        logger.info(log);
        log = String.format("Buggy instances: %d", numBuggy);
        logger.info(log);
        log = String.format("Buggy percentage: %f %%", ((float) numBuggy / dataset.size()) * 100.0);
        logger.info(log);
        log = String.format("Number of duplicated instances: %d", numDuplicates);
        logger.info(log);


        // Building the CSV file
        List<String[]> arrayOfCSVEntry = new ArrayList<>();
        // add headings
        String[] csvHeader = new String[]{
                "Release",
                "Filename",
                "Size",
                "LOC_touched",
                "LOC_added",
                "MAX_LOC_added",
                "AVG_LOC_added",
                "NR",
                "NAuth",
                "Churn",
                "MAX_Churn",
                "AVG_Churn",
                "NFix",
                "Age",
                "WeightedAge",
                "Buggy"};

        arrayOfCSVEntry.add(csvHeader);

        for (DatasetInstance entry : dataset) {
            arrayOfCSVEntry.add(entry.toStringArray());
        }

        CSVManager.csvWriteAll(projectName.toLowerCase(Locale.ROOT) + "_dataset.csv", arrayOfCSVEntry);


        /*--------------------------------------------------------------WEKA-----------------------------------------------------------*/

        // throw away the first 2 columns (release and filename)
        String[] wekaHeader = Arrays.copyOfRange(csvHeader, 2, csvHeader.length);
        WekaController wekaController = new WekaController(projectName, dataset, wekaHeader);

        /*
        TODO: maintain a list of different datasets in order to apply Walk Forward using training set with Snoring
        HINT: during the creation of the actual dataset, at the end of each release save the current state of the
        dataset and that is the dataset Snoring-affected at the i-th release.
         */
        List<ClassifierEvaluation> evaluations = wekaController.walkForward();
        List<String[]> evaluationsToCsv = new ArrayList<>();
        String[] header = new String[] {
                "Dataset",
                "#TrainingRelease",
                "Classifier",
                "Precision",
                "Recall",
                "AUC",
                "Kappa"
        };
        evaluationsToCsv.add(header);
        for (ClassifierEvaluation ce : evaluations){
            evaluationsToCsv.add(ce.toStringArray(projectName));
        }

        CSVManager.csvWriteAll(projectName + "_classifier_evaluation.csv", evaluationsToCsv);
    }


}
