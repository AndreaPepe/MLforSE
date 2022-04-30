package main;

import controller.BugManager;
import controller.VersionManager;
import csv.CSVManager;
import git.GitAnalyzer;
import git.GitCommitFactory;
import git.GitSingleton;
import jira.RetrieveReleases;
import jira.RetrieveTicketsID;
import logging.LoggerSingleton;
import model.Bug;
import model.GitCommit;
import model.JiraTicket;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Math.round;

public class Main {
    private static final String PROJECT_NAME = "SYNCOPE";


    public static void main(String[] args) throws Exception {
        Logger logger = LoggerSingleton.getInstance().getLogger();


        /*--------------------------------------------------------RELEASES-------------------------------------------------------*/

        /*
        The constructor of the VersionManager retrieves all the released version for the specified project,
        store them sorted chronologically, both all versions and the first half of them.
         */
        VersionManager versionManager = new VersionManager(PROJECT_NAME, logger);

        /*------------------------------------------------------JIRA TICKETS-----------------------------------------------------*/
        /*
         * Now, let's interact with Jira again to retrieve tickets of all fixed bugs
         * */
        ArrayList<JiraTicket> tickets = (ArrayList<JiraTicket>) RetrieveTicketsID.getTicketsID(PROJECT_NAME);
        /**for (JiraTicket ticket : tickets)
            logger.info(ticket.toString());*/


        /*
         * Now, for each ticket, let see if it is contained in the first half of the releases
         * */


        /*
         * Now, for each ticket, let see in which Git commit is present
         * */

        List<GitCommit> commits = new ArrayList<>();

        for (JiraTicket ticket : tickets) {
            try {
                GitAnalyzer analyzer = new GitAnalyzer();
                // get the entire log of commits
                Iterable<RevCommit> gitLog = analyzer.getGitLog(GitSingleton.getInstance().getGit());
                // here we pass the ticket as a keyword to be searched in commit message
                ArrayList<RevCommit> results = new ArrayList<>(analyzer.getCommitsContainingString(gitLog, ticket.getKey()));

                GitCommitFactory factory = GitCommitFactory.getInstance();
                for (RevCommit commit : results) {
                    commits.add(factory.parseCommit(commit, ticket.getKey()));
                }
            } catch (GitAPIException e) {
                logger.log(Level.SEVERE, String.format("Error for ticket: %s", ticket.getKey()), e);
            }
        }

        /**for (GitCommit commit : commits){
         logger.log(Level.INFO, commit.toString());
         }*/


        /*---------------------------------------------------------------------BUGS----------------------------------------------------------*/

        // Let's create a list of bugs objects from the jira tickets and then refine them with consistency checks
        List<Bug> bugs = new ArrayList<>();
        for (JiraTicket ticket : tickets) {
            bugs.add(new Bug(ticket));
        }

        // let's process bugs with commits to define fix commit and other commits for the specific bug
        bugs = BugManager.setFixCommitAndOtherCommits(bugs, commits);

        /* now, remove bugs with no bounded commit and add metadata on which is its fixing commit
         * That can be chosen in 2 ways:
         *  - if Jira fix date corresponds to commit date, then that commit is the fix commit;
         *  - otherwise, the latest commit referring that bug is chosen as FixCommit
         *
         * Bugs with no such commits are removed from the list.
         */

        bugs = BugManager.patchFixCommit(bugs);

        bugs = versionManager.calculateVersionsForBugs(bugs);
    }
}
