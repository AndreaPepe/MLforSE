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

    private String[] versionsArray;

    /*
    p = (FV - IV) / (FV - OV)
    predicted IV = FV - (FV - OV) * p
     */
    private float proportion;
    private int numberOfBugsUsedForProportion;
    private float aggregatedProportion;

    public Date getLatestReleaseDate() {
        return latestReleaseDate;
    }

    private Date latestReleaseDate;

    public VersionManager(String projectName, Logger logger) {
        this.logger = logger;
        this.projectName = projectName;
        this.proportion = 0;
        this.numberOfBugsUsedForProportion = 0;
        this.aggregatedProportion = 0;
    }

    public void setReleases() {
        String pathname = projectName.toLowerCase(Locale.ROOT) + RELEASES_PATH;
        try {
            List<Map.Entry<String, LocalDate>> releases = new ArrayList<>(RetrieveReleases.getReleases(projectName));
            releases.sort(Map.Entry.comparingByValue());

            Map<String, LocalDate> sortedVersions = new LinkedHashMap<>();
            List<LocalDate> addedDates = new ArrayList<>();
            for (Map.Entry<String, LocalDate> entry : releases) {
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
            Map<String, LocalDate> halfReleases = new LinkedHashMap<>();
            int size = sortedVersions.size();
            int count = 0;
            for (Map.Entry<String, LocalDate> entry : sortedVersions.entrySet()) {
                halfReleases.put(entry.getKey(), entry.getValue());
                count++;
                if (count >= round(size / 2.0)) {
                    break;
                }
            }

            this.versions = sortedVersions;
            this.halfVersions = halfReleases;
            LocalDate latestDate = null;
            Iterator<Map.Entry<String, LocalDate>> iterator = sortedVersions.entrySet().iterator();
            while (iterator.hasNext())
                latestDate = iterator.next().getValue();
            assert latestDate != null;
            this.latestReleaseDate = Date.from(latestDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
            this.versionsArray = new String[this.versions.size()];
            int i = 0;
            for (Map.Entry<String, LocalDate> entry : this.versions.entrySet()) {
                this.versionsArray[i] = entry.getKey();
                i++;
            }
        } catch (IOException | CSVException e) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Exception", e);
        }
    }

    public Map<String, List<RevCommit>> splitCommitsPerRelease(List<RevCommit> allCommits) {
        String commitDate;
        String release;
        List<RevCommit> commitsToRemove = new ArrayList<>();

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
                commitsToRemove.add(c);
            }
        }

        for (RevCommit c : commitsToRemove) {
            allCommits.remove(c);
        }

        return ret;
    }


    public List<Bug> calculateVersionsForBugs(List<Bug> bugs) {
        int numberOfProportion = 0;
        List<Bug> bugWithNoRelease = new ArrayList<>();
        for (Bug bug : bugs) {
            // find the opening version
            String openingDate = bug.getOpeningDate();
            String openingVersion = null;
            try {
                openingVersion = findVersionByDate(openingDate);
            } catch (CommitWithNoReleaseException e) {
                bugWithNoRelease.add(bug);
            }
            bug.setOpeningVersion(openingVersion);

            // find the fixed version
            String fixedDate = bug.getFixDate();
            String fixVersion = null;
            try {
                fixVersion = findVersionByDate(fixedDate);
            } catch (CommitWithNoReleaseException e) {
                bugWithNoRelease.add(bug);
            }
            bug.setFixVersion(fixVersion);

            /* For now, let's take only releases with affected versions already indicated
             */
            List<String> affVersions = bug.getAffectedVersions();
            boolean valid = areValidAffectedVersions(bug);
            if (valid) {
                // affected versions are ok; the oldest one is the injected Version
                String injectedVersion = findInjectedVersion(affVersions);
                bug.setInjectedVersion(injectedVersion);
                updateProportion(bug);
            } else {
                numberOfProportion++;
                /*
                Incremental proportion is used to identify the injected version
                and consequently define the affected versions
                 */
                String injectedVersion = findInjectedVersionUsingProportion(bug);
                List<String> affectedVersions = computeAffectedVersions(injectedVersion, fixVersion);
                bug.setInjectedVersion(injectedVersion);
                bug.setAffectedVersions(affectedVersions);
            }

        }

        for (Bug bug : bugWithNoRelease) {
            bugs.remove(bug);
        }
        logger.info(String.format("\nNumber of bug in which proportion has been used: %d", numberOfProportion));
        logger.info(String.format("Proportion p: %f", this.proportion));
        return bugs;
    }

    private void updateProportion(Bug bug) {
        int indexInjected = findIndexOfVersion(bug.getInjectedVersion());
        int indexFixed = findIndexOfVersion(bug.getFixVersion());
        int indexOpening = findIndexOfVersion(bug.getOpeningVersion());
        // calculate proportion only if the FV and the OV are different (in order to avoid infinite)
        if (indexFixed != indexOpening) {
            float myProportion = (float) (indexFixed - indexInjected) / (indexFixed - indexOpening);
            this.aggregatedProportion += myProportion;
            this.numberOfBugsUsedForProportion++;
            this.proportion = aggregatedProportion / numberOfBugsUsedForProportion;
        }
    }

    private List<String> computeAffectedVersions(String injectedVersion, String fixVersion) {
        List<String> aff = new ArrayList<>();
        boolean addToList = false;
        for (String s : this.versionsArray) {
            if (s.equals(injectedVersion))
                addToList = true;
            if (s.equals(fixVersion)) {
                return aff;
            }
            if (addToList)
                aff.add(s);
        }
        return aff;
    }

    private int findIndexOfVersion(String version) {
        for (int i = 0; i < this.versionsArray.length; i++) {
            if (this.versionsArray[i].equals(version)) {
                return i;
            }
        }
        return -1;
    }

    private String findInjectedVersionUsingProportion(Bug bug) {
        String fixVer = bug.getFixVersion();
        String openVer = bug.getOpeningVersion();
        int indexFix = findIndexOfVersion(fixVer);
        int indexOpening = findIndexOfVersion(openVer);
        int indexInjected = (int) Math.floor(indexFix - this.proportion * (indexFix - indexOpening));
        // predicted IV = FV - (FV - OV) * p
        if (indexInjected < 0)
            indexInjected = 0;
        return this.versionsArray[indexInjected];
    }

    private boolean areValidAffectedVersions(Bug bug) {
        List<String> affVersions = bug.getAffectedVersions();
        if (affVersions == null || affVersions.isEmpty())
            return false;
        // if the fixed version is classified as affected
        if (affVersions.contains(bug.getFixVersion())) {
            bug.setAffectedVersions(new ArrayList<>());
            return false;
        }
        // remove releases not considered
        affVersions.removeIf(s -> !this.versions.containsKey(s));
        if (affVersions.isEmpty()) {
            return false;
        }
        String injectedVersion = findInjectedVersion(affVersions);

        // if the injected version is after the opening version
        LocalDate injectedDate = this.versions.get(injectedVersion);
        LocalDate openingDate = this.versions.get(bug.getOpeningVersion());
        if (injectedDate.isAfter(openingDate)) {
            bug.setAffectedVersions(new ArrayList<>());
            return false;
        }
        bug.setAffectedVersions(affVersions);
        return true;
    }

    private String findInjectedVersion(List<String> affVersions) {
        for (Map.Entry<String, LocalDate> release : this.versions.entrySet()) {
            // since releases are ordered by date, the first one that is in the affected versions list is the injected version
            if (affVersions.contains(release.getKey())) {
                return release.getKey();
            }
        }
        return null;
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
            throw new CommitWithNoReleaseException("No release found for the commit; probably it's parte of the still unreleased version!");
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


    public String findNextVersion(String version) {
        for (int i = 0; i < versionsArray.length - 1; i++) {
            if (versionsArray[i].equals(version))
                return versionsArray[i + 1];
        }
        // the latest version
        return null;
    }

    public int getReleasesSize() {
        return this.versions.size();
    }

    public Map<String, LocalDate> getHalfVersions() {
        return this.halfVersions;
    }

    public LocalDate getReleaseDateOfVersion(String version) {
        for (Map.Entry<String, LocalDate> entry : this.versions.entrySet()) {
            if (entry.getKey().equals(version))
                return entry.getValue();
        }
        return null;
    }
}
