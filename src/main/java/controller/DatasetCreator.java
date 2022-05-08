package controller;

import model.Bug;
import model.DatasetInstance;
import model.GitCommit;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Logger;

public class DatasetCreator {
    private final VersionManager versionManager;
    private final GitManager gitManager;
    private final List<Bug> bugs;
    private final Logger logger;

    private List<DatasetInstance> dataset;




    public DatasetCreator(VersionManager versionManager, GitManager gitManager, List<Bug> bugs, Logger logger){
        this.versionManager = versionManager;
        this.gitManager = gitManager;
        this.bugs = bugs;
        this.logger = logger;
    }

    /**
     * This method analyze the entire git log, making diff between subsequents
     * commits and reconstructing the history of each file.
     * It also computes metrics and detect buggyness.
     *
     * @param gitLog     mapping of release with commits
     * @param fixCommits a list of chronologically sorted fix commits
     * @return the list of instances for the dataset
     */
    public List<DatasetInstance> computeDataset(Map<String, List<RevCommit>> gitLog, List<GitCommit> fixCommits) throws IOException {
        dataset = new ArrayList<>();
        RevCommit prev = null;
        for (Map.Entry<String, List<RevCommit>> release: gitLog.entrySet()){
            // for each release
            logger.info("Release: " + release.getKey() + " Commits: " + release.getValue().size());
            for (RevCommit current: release.getValue()){
                // for each commit
                dataset.addAll(analyzeCommitPair(release.getKey(), prev, current));

                prev = current;
            }
        }

        return dataset;
    }

    private List<DatasetInstance> analyzeCommitPair(String release, RevCommit prev, RevCommit current) throws IOException {
        List<DiffEntry> diffs = gitManager.makeDiff(prev, current);
        List<DatasetInstance> instances = new ArrayList<>();
        boolean isFixCommit = isFixCommit(current);
        for (DiffEntry diff: diffs){
            switch (diff.getChangeType()){
                case ADD -> handleAdd(diff, release, current);
                case COPY -> handleCopy(diff, release);
                case DELETE -> handleDelete(diff, release);
                case MODIFY -> handleModify(diff, release, isFixCommit);
                case RENAME -> {}//handleRename(diff, release);
            }
        }
        return instances;
    }

    private boolean isFixCommit(RevCommit current) {
        for(Bug bug: bugs){
            if(current.equals(bug.getFixCommit().getRevCommit()))
                return true;
            else if (bug.getOtherCommits() != null){
                for (GitCommit otherCommit : bug.getOtherCommits()){
                    if (current.equals(otherCommit.getRevCommit()))
                        return true;
                }
            }
        }
        return false;
    }



    private void handleAdd(DiffEntry entry, String release, RevCommit commit) {
        PersonIdent author = commit.getAuthorIdent();
        LocalDate creationDate = author.getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        DatasetInstance instance = new DatasetInstance(release, entry.getNewPath(), author.getName(), creationDate, false);
        // manage LOC changes
        dataset.add(instance);
    }

    private void handleCopy(DiffEntry entry, String release) {
        // copy an existing file to a new location maintaining the content
        // let's add the new file, but we must also transfer buggyness of the existing file
        String oldName = entry.getOldPath();
        String newName = entry.getNewPath();
        boolean buggy = false; // by default
        for (DatasetInstance instance : dataset) {
            if (instance.getFilename().equals(oldName)) {
                // the old file was already in the list, so let's take it's buggyness
                buggy = instance.isBuggy();
            }
        }
        dataset.add(new DatasetInstance(release, newName, buggy));
    }

    private void handleDelete(DiffEntry entry, String release) {
        dataset.removeIf(csvEntry -> entry.getNewPath().equals(csvEntry.getFilename()));
    }

    private void handleModify(DiffEntry entry, String release, boolean isFixCommit) {
        DatasetInstance foundEntryWithSameRelease = null;
        for (DatasetInstance instance : dataset) {
            if (instance.getFilename().equals(entry.getNewPath())) {
                // if the file has been modified in that release, set it to buggy
                if (instance.getVersion().equals(release)) {
                    foundEntryWithSameRelease = instance;
                    break;
                }
            }
        }

        if (foundEntryWithSameRelease != null) {
            foundEntryWithSameRelease.setBuggy(true);
        } else {
            dataset.add(new DatasetInstance(release, entry.getNewPath(), true));
        }

    }

    private void handleRename(DiffEntry entry, String release) {
        for (ListIterator<DatasetInstance> iter = dataset.listIterator(); iter.hasNext();) {
            DatasetInstance instance = iter.next();
            if (instance.getFilename().equals(entry.getOldPath())) {
                if (instance.getVersion().equals(release)) {
                    // change the path name
                    instance.setFilename(entry.getNewPath());
                } else if (versionManager.isAfterVersion(release, instance.getVersion())) {
                    // if the actual release is after the previous release present in the list, I have to add a new entry
                    // the buggy attribute is inherited
                    iter.add(new DatasetInstance(release, entry.getNewPath(), instance.isBuggy()));
                }
            }
        }
    }
}
