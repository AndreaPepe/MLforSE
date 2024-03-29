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
import weka.CostSensitivity;

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
        The setReleases() method of the VersionManager class retrieves all the released version for the specified project,
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
        List<JiraTicket> tickets = RetrieveTicketsID.getTicketsID(projectName);
        log = String.format("Jira tickets: %d", tickets.size());
        logger.info(log);

        /*---------------------------------------------------------------------GIT-------------------------------------------------------------*/
        /*
         * Now, for each ticket, let see in which Git commit it is present
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

        refineBugsList(bugs, fixCommits);

        bugs = versionManager.calculateVersionsForBugs(bugs);
        logger.info("\nIdentification of FV, OV, AVs and IV for bugs. DONE");

        Map<String, List<RevCommit>> commitPerRelease = versionManager.splitCommitsPerRelease(allCommits);
        logger.info("\nSplit commits by releases. DONE");

        /*-----------------------------------------------DATASET CREATION------------------------------------------------------*/

        GitManager gitManager = new GitManager(GitSingleton.getInstance().getGit());
        DatasetCreator datasetCreator = new DatasetCreator(versionManager, gitManager, bugs, logger);

        logger.info("\nDataset creation begins ...\n");
        List<DatasetInstance> dataset = datasetCreator.computeDataset(commitPerRelease);
        Map<String, List<DatasetInstance>> datasetsWithSnoring = datasetCreator.getMultipleDatasets();
        logger.info("\nDataset creation. DONE");

        // remove duplicated instances from both dataset and datasetWithSnoring
        removeDatasetDuplicates(dataset, datasetsWithSnoring, logger);

        // Building the CSV file
        List<String[]> arrayOfCSVEntry = new ArrayList<>();
        // add headings
        String[] csvHeader = buildDatasetHeader();

        arrayOfCSVEntry.add(csvHeader);

        for (DatasetInstance entry : dataset) {
            arrayOfCSVEntry.add(entry.toStringArray());
        }

        CSVManager.csvWriteAll(projectName.toLowerCase(Locale.ROOT) + "_dataset.csv", arrayOfCSVEntry);


        /*--------------------------------------------------------------WEKA-----------------------------------------------------------*/

        // throw away the first 2 columns (release and filename)
        String[] wekaHeader = Arrays.copyOfRange(csvHeader, 2, csvHeader.length);

        WekaController wekaController = new WekaController(projectName, datasetsWithSnoring, wekaHeader);
        logger.info("Walk Forward technique to evaluate classifiers is running ...");

        // Do the comparison of results changing the cost sensitivity technique
        for (CostSensitivity sensitivity : CostSensitivity.values()) {
            log = "Classifiers evaluation with cost sensitivity policy: " + sensitivity.toString();
            logger.info(log);
            List<ClassifierEvaluation> evaluations = wekaController.walkForwardWithSnoring(sensitivity);
            List<String[]> evaluationsToCsv = new ArrayList<>();
            String[] header = buildClassifiersHeader();
            evaluationsToCsv.add(header);
            for (ClassifierEvaluation ce : evaluations) {
                evaluationsToCsv.add(ce.toStringArray(projectName));
            }
            StringBuilder builder = new StringBuilder();
            builder.append(projectName).append("_").append(sensitivity.toString()).append(".csv");
            CSVManager.csvWriteAll(builder.toString(), evaluationsToCsv);
        }
    }

    private static void refineBugsList(List<Bug> bugs, List<GitCommit> fixCommits){
        // let's process bugs with commits to define fix commit and other commits for the specific bug
        BugManager.setFixCommitAndOtherCommits(bugs, fixCommits);

        /** now, remove bugs with no bounded commit and add metadata on which is its fixing commit
         * That can be chosen in 2 ways:
         *  - if Jira fix date corresponds to commit date, then that commit is the fix commit
         *  - otherwise, the latest commit referring that bug is chosen as FixCommit
         *
         * Bugs with no such commits are removed from the list.
         */

        BugManager.patchFixCommit(bugs);

        // sort bugs by time to avoid influence of future bugs on data of the past
        // this allows to build training datasets Snoring-affected to be used in Walking Forward approach to validate the predictor
        BugManager.sortBugsChronologically(bugs);
    }


    private static void removeDatasetDuplicates(List<DatasetInstance> dataset, Map<String,List<DatasetInstance>> datasetWithSnoring, Logger logger){
        int numBuggy = 0;
        int numDuplicates = 0;

        HashMap<String, List<DatasetInstance>> datasetByRelease = new HashMap<>();
        for (DatasetInstance instance : dataset) {
            if (!datasetByRelease.containsKey(instance.getVersion())) {
                datasetByRelease.put(instance.getVersion(), new ArrayList<>());
            }
            datasetByRelease.get(instance.getVersion()).add(instance);
        }

        List<DatasetInstance> toRemove = new ArrayList<>();
        for (Map.Entry<String, List<DatasetInstance>> release : datasetByRelease.entrySet()) {
            List<String> filenames = new ArrayList<>();

            for (DatasetInstance instance : release.getValue()) {
                if (instance.isBuggy()) {
                    numBuggy++;
                }

                if (filenames.contains(instance.getFilename())) {
                    numDuplicates++;
                    toRemove.add(instance);
                } else {
                    filenames.add(instance.getFilename());
                }
            }
        }

        dataset.removeAll(toRemove);

        for (DatasetInstance i : toRemove){
            datasetWithSnoring.get(i.getVersion()).remove(i);
        }

        logger.info(System.getProperty("line.separator"));
        String log = String.format("Dataset size: %d instances", dataset.size());
        logger.info(log);
        log = String.format("Buggy instances: %d", numBuggy);
        logger.info(log);
        log = String.format("Buggy percentage: %f %%", ((float) numBuggy / dataset.size()) * 100.0);
        logger.info(log);
        log = String.format("Number of duplicated instances: %d", numDuplicates);
        logger.info(log);
    }


    private static String[] buildDatasetHeader(){
        return new String[]{
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
    }


    private static String[] buildClassifiersHeader(){
        return new String[]{
                "Dataset",
                "#TrainingRelease",
                "%Training",
                "%DefectiveInTraining",
                "%DefectiveInTesting",
                "Classifier",
                "Balancing",
                "FeatureSelection",
                "Sensitivity",
                "TP",
                "FP",
                "TN",
                "FN",
                "Precision",
                "Recall",
                "AUC",
                "Kappa"
        };
    }
}
