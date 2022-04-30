package main;

import csv.CSVManager;
import git.GitAnalyzer;
import git.GitSingleton;
import jira.RetrieveReleases;
import jira.RetrieveTicketsID;
import logging.LoggerSingleton;
import model.GitCommit;
import model.JiraTicket;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.lang.Math.round;

public class Main {
    private static final String PROJECT_NAME = "SYNCOPE";
    private static final String RELEASES_PATH = "syncope_releases.csv";

    public static void main (String[] args) throws Exception {
        Logger logger = LoggerSingleton.getInstance().getLogger();
        /* As first thing, retrieve all released version with releaseDate */
        try {
            ArrayList<String[]> versions = (ArrayList<String[]>) RetrieveReleases.getReleases(PROJECT_NAME);

            // write versions to csv file
            CSVManager.csvWriteAll(RELEASES_PATH, versions);

            /* maintain only the first half of releases */
            List<String[]> halfVersions = versions.subList(0, (int) round(versions.size()/2.0));
            /**for (String[] version : halfVersions) {
                System.out.printf("%-30.30s  %-30.30s%n", version[0], version[1]);
            }*/

            /*
            * Now, let's interact with Jira again to retrieve tickets of all fixed bugs
            * */
        } catch (IOException e) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Exception", e);
        }

            ArrayList<JiraTicket> tickets = (ArrayList<JiraTicket>) RetrieveTicketsID.getTicketsID(PROJECT_NAME);
            for (JiraTicket ticket : tickets)
                logger.log(Level.INFO,"{0}", ticket.toString());


            /*
             * Now, for each ticket, let see if it is contained in the first half of the releases
             * */


            /*
            * Now, for each ticket, let see in which Git commit is present
            * */

            List<GitCommit> commits = new ArrayList<>();

            for (JiraTicket ticket: tickets){
                try {
                    GitAnalyzer analyzer = new GitAnalyzer();
                    Iterable<RevCommit> log = analyzer.getGitLog(GitSingleton.getInstance().getGit());
                    // here we pass the ticket as a keyword to be searched in commit message
                    ArrayList<RevCommit> results = new ArrayList<>(analyzer.getCommitsContainingString(log, ticket.getKey()));

                    for (RevCommit commit: results){
                        String id = commit.getId().toString().split(" ")[1];
                        String shortId = commit.getId().toString().split(" ")[2];
                        /* Substitute with the method 'getFullMessage()'
                        if you want to read the whole commit message plus other infos */
                        String msg = commit.getShortMessage();

                        String author = commit.getCommitterIdent().getName();
                        String date = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date(commit.getCommitTime() * 1000L));
                        GitCommit comm = new GitCommit(id, shortId, date, author, msg, ticket.getKey());
                        commits.add(comm);
                    }
                } catch (GitAPIException e) {
                    logger.log(Level.SEVERE, String.format("Error for ticket: {0}", ticket.getKey()), e);
                }
            }

            /**for (GitCommit commit : commits){
                logger.log(Level.INFO, commit.toString());
            }*/
    }
}
