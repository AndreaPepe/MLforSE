package main;

import csv.CSVManager;
import jira.RetrieveReleases;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.lang.Math.round;

public class Main {
    private static final String PROJECT_NAME = "SYNCOPE";
    private static final String RELEASES_PATH = "syncope_releases.csv";

    public static void main (String[] args) throws Exception {
        /* As first thing, retrieve all released version with releaseDate */
        try {
            ArrayList<String[]> versions = (ArrayList<String[]>) RetrieveReleases.getReleases(PROJECT_NAME);

            // write versions to csv file
            CSVManager.csvWriteAll(RELEASES_PATH, versions);

            /* maintain only the first half of releases */
            List<String[]> halfVersions = versions.subList(0, (int) round(versions.size()/2.0));
            for (String[] version : halfVersions) {
                System.out.printf("%-30.30s  %-30.30s%n", version[0], version[1]);
            }
        } catch (IOException e) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Exception", e);
        }
    }
}
