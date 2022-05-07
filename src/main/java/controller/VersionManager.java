package controller;

import csv.CSVManager;
import exceptions.CSVException;
import exceptions.CommitWithNoReleaseException;
import jira.RetrieveReleases;
import main.Main;
import model.Bug;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Math.round;

public class VersionManager {

    private Map<String, LocalDate> versions;
    private Map<String, LocalDate> halfVersions;
    private final String projectName;
    private static final String RELEASES_PATH = "_releases.csv";
    private final Logger logger;

    public Date getLatestReleaseDate() {
        return latestReleaseDate;
    }

    private Date latestReleaseDate;

    public VersionManager(String projectName, Logger logger) {
        this.logger = logger;
        this.projectName = projectName;
    }

    public void setReleases() {
        String pathname = projectName.toLowerCase(Locale.ROOT) + RELEASES_PATH;
        try {
            List<Map.Entry<String, LocalDate>> versions = new ArrayList<>(RetrieveReleases.getReleases(projectName));
            versions.sort(Map.Entry.comparingByValue());

            Map<String, LocalDate> sortedVersions = new LinkedHashMap<>();
            List<LocalDate> addedDates = new ArrayList<>();
            for (Map.Entry<String, LocalDate> entry : versions) {
                if (!addedDates.contains(entry.getValue())) {
                    // remove releases that have the same release date
                    sortedVersions.put(entry.getKey(), entry.getValue());
                    addedDates.add(entry.getValue());
                }

            }

            // write versions to csv file
            List<String[]> lines = new ArrayList<>();
            lines.add(new String[]{"Version", "Release Date"});
            for (Map.Entry<String, LocalDate> entry : sortedVersions.entrySet()) {
                lines.add(new String[]{entry.getKey(), String.valueOf(entry.getValue())});
            }
            CSVManager.csvWriteAll(pathname, lines);

            /* maintain only the first half of releases */
            Map<String, LocalDate> halfVersions = new LinkedHashMap<>();
            int size = sortedVersions.size();
            int count = 0;
            for (Map.Entry<String, LocalDate> entry : sortedVersions.entrySet()) {
                halfVersions.put(entry.getKey(), entry.getValue());
                count++;
                if (count >= round(size / 2.0)) {
                    break;
                }
            }

            this.versions = sortedVersions;
            this.halfVersions = halfVersions;
            LocalDate latestDate = null;
            Iterator<Map.Entry<String, LocalDate>> iterator = sortedVersions.entrySet().iterator();
            while (iterator.hasNext())
                latestDate = iterator.next().getValue();
            assert latestDate != null;
            this.latestReleaseDate = Date.from(latestDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            /*----LOG----*/
            //logger.info(halfVersions.toString());

        } catch (IOException | CSVException e) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Exception", e);
        }
    }

    public Map<String, List<RevCommit>> splitCommitsPerRelease(List<RevCommit> allCommits) {
        String commitDate;
        String release;

        Map<String, List<RevCommit>> ret = new LinkedHashMap<>();
        for (RevCommit c : allCommits) {
            commitDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date(c.getCommitTime() * 1000L));
            try {
                release = findVersionByDate(commitDate);

                if (ret.containsKey(release))
                    ret.get(release).add(c);
                else {
                    List<RevCommit> commitsOfTheRelease = new ArrayList<>();
                    commitsOfTheRelease.add(c);
                    ret.put(release, commitsOfTheRelease);
                }
            } catch (CommitWithNoReleaseException e) {
                // if the commit has no release, let's remove it from the RevCommit list
                logger.info("Commit with no release: " + c.toString());
            }
        }
        logger.info(ret.keySet().toString());
        return ret;
    }


    public List<Bug> calculateVersionsForBugs(List<Bug> bugs) throws CommitWithNoReleaseException {
        long proportion = 0;
        long maxDistance = 0;
        int count = 0;
        for (Bug bug : bugs) {
            // find the opening version
            String openingDate = bug.getOpeningDate();
            String openingVersion = findVersionByDate(openingDate);
            bug.setOpeningVersion(openingVersion);

            // find the fixed version
            String fixedDate = bug.getFixDate();
            String fixVersion = findVersionByDate(fixedDate);
            bug.setFixVersion(fixVersion);

            /* For now, let's take only releases with affected versions already indicated
             */
            List<String> affVersions = bug.getAffectedVersions();
            if (affVersions == null) {
                bugs.remove(bug);
            }

            /**long daysBetween = LocalDate.parse(fixedDate).minusDays(LocalDate.parse(openingDate).toEpochDay()).toEpochDay();
             if(daysBetween > maxDistance)
             maxDistance = daysBetween;
             proportion += daysBetween;
             count ++;*/
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
        /*double proportionFactor = (double)proportion/count;
        logger.info("\nProportion factor = " + proportionFactor);
        logger.info("\nMax Distance (in days)" + maxDistance);*/
        return bugs;
    }


    /**
     * Iterates the linked (and sorted) list of versions until it finds one that has
     * a release date that is equal or after the input date. In that case, that is the version.
     *
     * @param date String in 'yyyy-MM-dd' format of the date
     * @return the name of the OV
     */
    public String findVersionByDate(String date) throws CommitWithNoReleaseException {
        LocalDate openDate = LocalDate.parse(date);
        String result = null;
        for (Map.Entry<String, LocalDate> entry : this.versions.entrySet()) {
            if (entry.getValue().isEqual(openDate) || entry.getValue().isAfter(openDate)) {
                result = entry.getKey();
                break;
            }
        }
        if (result == null) {
            // date is after the date of the latest released version, so it's part of the current release
            logger.log(Level.SEVERE, "No VERSION FOUND! Date: " + date);
            throw new CommitWithNoReleaseException("No version found");
        }
        return result;
    }

    public boolean isAfterVersion(String versionName, String targetVersion) {
        LocalDate versionDate = null;
        LocalDate targetDate = null;
        for (Map.Entry<String, LocalDate> entry : this.versions.entrySet()) {
            if (entry.getKey().equals(versionName)) {
                versionDate = entry.getValue();
            }
            if (entry.getKey().equals(targetVersion)) {
                targetDate = entry.getValue();
            }
            if (versionDate != null && targetDate != null)
                break;
        }
        assert versionDate != null;
        assert targetDate != null;
        return versionDate.isAfter(targetDate);
    }


}
