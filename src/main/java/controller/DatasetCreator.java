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


    public DatasetCreator(VersionManager versionManager, GitManager gitManager, List<Bug> bugs, Logger logger) {
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
        for (Map.Entry<String, List<RevCommit>> release : gitLog.entrySet()) {
            // for each release
            logger.info("Release: " + release.getKey() + " Commits: " + release.getValue().size());
            for (RevCommit current : release.getValue()) {
                // for each commit
                dataset.addAll(analyzeCommitPair(release.getKey(), prev, current));

                prev = current;
            }
            //TODO: at the end of each release, copy all the existing instances in the following release
            String nextRelease = versionManager.findNextVersion(release.getKey());
            if(nextRelease != null){
                // only if the current release is not the last one
                List<DatasetInstance> instancesAtTheEndOfTheRelease = new ArrayList<>();

                for (DatasetInstance instance : dataset){
                    if (instance.getVersion().equals(release.getKey())){
                        instancesAtTheEndOfTheRelease.add(new DatasetInstance(instance, nextRelease));
                    }
                }

                dataset.addAll(instancesAtTheEndOfTheRelease);
            }

        }

        return dataset;
    }

    private List<DatasetInstance> analyzeCommitPair(String release, RevCommit prev, RevCommit current) throws IOException {
        List<DiffEntry> diffs = gitManager.makeDiff(prev, current);
        List<DatasetInstance> instances = new ArrayList<>();
        boolean isFixCommit = isFixCommit(current);
        for (DiffEntry diff : diffs) {
            switch (diff.getChangeType()) {
                case ADD -> handleAdd(diff, release, current);
                case COPY -> handleCopy(diff, release);
                case DELETE -> handleDelete(diff, release);
                case MODIFY -> handleModify(diff, release, current, isFixCommit);
                case RENAME -> handleRename(diff, release);
            }
        }
        return instances;
    }

    private boolean isFixCommit(RevCommit current) {
        for (Bug bug : bugs) {
            if (current.equals(bug.getFixCommit().getRevCommit()))
                return true;
            else if (bug.getOtherCommits() != null) {
                for (GitCommit otherCommit : bug.getOtherCommits()) {
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
        DatasetInstance instance = new DatasetInstance(release, entry.getNewPath(), creationDate, false);
        instance.addAuthor(author.getName());
        // TODO:manage LOC changes
        dataset.add(instance);
    }

    private void handleCopy(DiffEntry entry, String release) {
        // copy an existing file to a new location maintaining the content
        // let's add the new file, but we must also transfer buggyness of the existing file
        String oldName = entry.getOldPath();
        String newName = entry.getNewPath();
        int idx = getLatestInstanceByName(oldName);
        if(idx < 0)
            return;
        DatasetInstance newInstance = new DatasetInstance(dataset.get(idx), release);
        newInstance.setFilename(newName);
        dataset.add(newInstance);
    }

    private void handleDelete(DiffEntry entry, String release) {
        // remove only if there is an instance with the same release
        int idx = getLatestInstanceByName(entry.getNewPath());
        if (idx < 0)
            return;
        if (this.dataset.get(idx).getVersion().equals(release))
            dataset.remove(idx);

        //TODO: update age maybe
    }

    private void handleModify(DiffEntry entry, String release, RevCommit commit, boolean isFixCommit) {
        int indexLatest = getLatestInstanceByName(entry.getNewPath());
        if (indexLatest < 0) {
            // file does not exist
            return;
        }
        //old instance
        DatasetInstance instance = this.dataset.get(indexLatest);
        boolean sameRelease = instance.getVersion().equals(release);
        if (!sameRelease) {
            // create a new instance from the previous one, maintaining stats
            instance = new DatasetInstance(instance, release);
            instance.setBuggy(false);
            this.dataset.add(instance);
        }

        // Get bugs for detection of BUGGYNESS
        List<Bug> bugs = getBugsOfCommit(this.bugs, commit);
        Set<String> affectedVersions = new HashSet<>();
        for (Bug bug : bugs) {
            affectedVersions.addAll(bug.getAffectedVersions());
        }

        // Set buggy true
        for (String av : affectedVersions) {
            //TODO: check also for previous filenames, not only entry.getNewPath()
            int idx = getLatestInstanceByNameAndRelease(entry.getNewPath(), av);
            if (idx >= 0)
                this.dataset.get(idx).setBuggy(true);
        }

        //TODO: update stats like set of bug fixed, ...
        instance.addAuthor(commit.getAuthorIdent().getName());
        //TODO: compute LOC changes and so on ...
    }

    private void handleRename(DiffEntry entry, String release) {
        int idx = getLatestInstanceByName(entry.getOldPath());
        if(idx < 0)
            return;
        if (dataset.get(idx).getVersion().equals(release)){
            // if in the same release, change only the name
            dataset.get(idx).addPreviousName(entry.getOldPath());
            dataset.get(idx).setFilename(entry.getNewPath());
        }else{
            DatasetInstance newInstance = new DatasetInstance(dataset.get(idx), release);
            newInstance.addPreviousName(entry.getOldPath());
            newInstance.setFilename(entry.getNewPath());
            dataset.remove(idx);
            dataset.add(newInstance);
        }
    }

    private int getLatestInstanceByName(String filename) {
        int i = 0;
        int max = -1;
        for (DatasetInstance instance : this.dataset) {
            if (instance.getFilename().equals(filename)) {
                max = i;
            }
            i++;
        }
        return max;
    }

    private int getLatestInstanceByNameAndRelease(String filename, String release) {
        int i = 0;
        int max = 0;
        for (DatasetInstance instance : this.dataset) {
            if (instance.getFilename().equals(filename) && instance.getVersion().equals(release)) {
                max = i;
            }
            i++;
        }
        return max;
    }

    private List<Bug> getBugsOfCommit(List<Bug> bugs, RevCommit commit) {
        Set<Bug> bugsRetrieved = new HashSet<>();
        for (Bug bug : bugs) {
            if (bug.getFixCommit().getRevCommit().equals(commit)) {
                bugsRetrieved.add(bug);
            } else if (bug.getOtherCommits() != null) {
                for (GitCommit gitCommit : bug.getOtherCommits()) {
                    if (gitCommit.getRevCommit().equals(commit))
                        bugsRetrieved.add(bug);
                }
            }
        }
        return new ArrayList<>(bugsRetrieved);
    }
}
