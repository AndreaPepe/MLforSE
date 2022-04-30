package controller;

import csv.CSVManager;
import exceptions.CSVException;
import jira.RetrieveReleases;
import main.Main;
import model.Bug;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Math.round;

public class VersionManager {

    private Map<LocalDate, String> versions;
    private Map<LocalDate, String> halfVersions;
    private String projectName;
    private static String RELEASES_PATH = "_releases.csv";
    private Logger logger;

    public VersionManager(String projectName, Logger logger){
        this.logger = logger;
        this.projectName = projectName;
        RELEASES_PATH = projectName.toLowerCase(Locale.ROOT) + RELEASES_PATH;
        try {
            List<Map.Entry<LocalDate, String>> versions = new ArrayList<>(RetrieveReleases.getReleases(projectName));
            versions.sort(new Comparator<Map.Entry<LocalDate, String>>() {
                @Override
                public int compare(Map.Entry<LocalDate, String> a, Map.Entry<LocalDate, String> b) {
                    return a.getKey().compareTo(b.getKey());
                }
            });

            Map<LocalDate, String> sortedVersions = new LinkedHashMap<>();
            for (Map.Entry<LocalDate, String> entry : versions) {
                sortedVersions.put(entry.getKey(), entry.getValue());
            }

            // write versions to csv file
            List<String[]> lines = new ArrayList<>();
            lines.add(new String[]{"Version", "Release Date"});
            for (Map.Entry<LocalDate, String> entry : sortedVersions.entrySet()) {
                lines.add(new String[]{entry.getValue(), entry.getKey().toString()});
            }
            CSVManager.csvWriteAll(RELEASES_PATH, lines);

            /* maintain only the first half of releases */
            Map<LocalDate, String> halfVersions = new LinkedHashMap<>();
            int size = sortedVersions.size();
            int count = 0;
            for (Map.Entry<LocalDate, String> entry : sortedVersions.entrySet()) {
                halfVersions.put(entry.getKey(), entry.getValue());
                count++;
                if (count >= round(size / 2.0)) {
                    break;
                }
            }

            this.versions = sortedVersions;
            this.halfVersions = halfVersions;
            /*----LOG----*/
            logger.info(halfVersions.toString());

        } catch (IOException | CSVException e) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Exception", e);
        }
    }

    public List<Bug> calculateVersionsForBugs(List<Bug> bugs){
        long proportion = 0;
        long maxDistance = 0;
        int count = 0;
        for (Bug bug: bugs){
            // find the opening version
            String openingDate = bug.getOpeningDate();
            String openingVersion = findVersionByDate(openingDate);
            bug.setOpeningVersion(openingVersion);

            // find the fixed version
            String fixedDate = bug.getFixDate();
            String fixVersion = findVersionByDate(fixedDate);
            bug.setFixVersion(fixVersion);

            long daysBetween = LocalDate.parse(fixedDate).minusDays(LocalDate.parse(openingDate).toEpochDay()).toEpochDay();
            if(daysBetween > maxDistance)
                maxDistance = daysBetween;
            proportion += daysBetween;
            count ++;
            /*
            List<String> affected = bug.getAffectedVersions();
           if (affected != null && !affected.isEmpty()){
               Map<LocalDate, String> versions = new LinkedHashMap<>();
               for (Map.Entry<LocalDate, String> entry : this.versions.entrySet()){
                   if (affected.contains(entry.getValue()))
                       versions.put(entry.getKey(), entry.getValue());
               }
           }*/
        }
        double proportionFactor = (double)proportion/count;
        logger.info("\nProportion factor = " + proportionFactor);
        logger.info("\nMax Distance (in days)" + maxDistance);
        return bugs;
    }


    /**
     * Iterates the linked (and sorted) list of versions until it finds one that has
     * a release date that is equal or after the input date. In that case, that is the version.
     * @param date String in 'yyyy-MM-dd' format of the date
     * @return the name of the OV
     */
    private String findVersionByDate(String date){
        LocalDate openDate = LocalDate.parse(date);
        String result = null;
        for (Map.Entry<LocalDate, String> entry: this.versions.entrySet()){
            if (entry.getKey().isEqual(openDate) || entry.getKey().isAfter(openDate)){
                result = entry.getValue();
                break;
            }
        }
        if (result == null)
            logger.log(Level.SEVERE, "No VERSION FOUND!");
        return result;
    }

}
