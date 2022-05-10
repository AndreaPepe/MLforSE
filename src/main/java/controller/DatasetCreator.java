package controller;

import model.Bug;
import model.DatasetInstance;
import model.GitCommit;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.time.Duration;
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
     * It also computes metrics and detect buggy files.
     *
     * @param gitLog mapping of release with commits
     * @return the list of instances for the dataset
     */
    public List<DatasetInstance> computeDataset(Map<String, List<RevCommit>> gitLog) throws IOException {
        gitManager.setDiffFormatter(); // prepare the diff formatter to filter only java files, excluding tests
        dataset = new ArrayList<>();
        RevCommit prev = null;
        int indexOfCurrentRelease = 0;
        String log;
        for (Map.Entry<String, List<RevCommit>> release : gitLog.entrySet()) {
            // for each release
            log = String.format("Release: %s\tCommits: %d", release.getKey(), release.getValue().size());
            logger.info(log);
            for (RevCommit current : release.getValue()) {
                // for each commit
                analyzeCommitPair(release.getKey(), prev, current);

                prev = current;
            }


            /* At the end of each release (except the last one), copy each file present in the dataset
             as a file present in the next release.
             Deletion, modification and so on will count on this!
             */
            String nextRelease = versionManager.findNextVersion(release.getKey());
            // the size is kept before adding the files of the next release
            int newValueForIndex = dataset.size();
            if (nextRelease != null) {
                // only if the current release is not the last one

                List<DatasetInstance> nextReleaseInstances = new ArrayList<>();
                for (int i = indexOfCurrentRelease; i < dataset.size(); i++){
                    // each file present at the end of the current release is initially present also in the next release
                    nextReleaseInstances.add(new DatasetInstance(dataset.get(i), nextRelease));
                    // compute age at the end of each release for each instance in that release
                    computeAge(dataset.get(i), release.getKey());
                }

                dataset.addAll(nextReleaseInstances);
            }

            // increment the index to the first entry of the next release
            indexOfCurrentRelease = newValueForIndex;
        }

        // cut the dataset to the first half of releases
        cutDatasetInHalf();
        return dataset;
    }

    private void analyzeCommitPair(String release, RevCommit prev, RevCommit current) throws IOException {
        List<DiffEntry> diffs = gitManager.makeDiff(prev, current);
        for (DiffEntry diff : diffs) {
            switch (diff.getChangeType()) {
                case ADD -> handleAdd(diff, release, current);
                case COPY -> handleCopy(diff, release, current);
                case DELETE -> handleDelete(diff, release);
                case MODIFY -> handleModify(diff, release, current);
                case RENAME -> handleRename(diff, release, current);
            }
        }
    }

    private void handleAdd(DiffEntry entry, String release, RevCommit commit) {
        PersonIdent author = commit.getAuthorIdent();
        LocalDate creationDate = author.getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        DatasetInstance instance = new DatasetInstance(release, entry.getNewPath(), creationDate, false);
        instance.addAuthor(author.getName());
        // number of Revisions does not need to be incremented because it's already initialized to 1
        computeLocChanges(entry, instance);
        dataset.add(instance);
    }

    private void handleCopy(DiffEntry entry, String release, RevCommit commit) {
        PersonIdent author = commit.getAuthorIdent();
        // copy an existing file to a new location maintaining the content
        // let's add the new file, but we must also transfer the buggy attribute of the existing file
        String oldName = entry.getOldPath();
        String newName = entry.getNewPath();
        int idx = getLatestInstanceByName(oldName);
        if (idx < 0)
            return;
        DatasetInstance newInstance = new DatasetInstance(dataset.get(idx), release);
        newInstance.setFilename(newName);

        // increment the number of revisions of both instances
        dataset.get(idx).incrementNumberOfRevisions();
        newInstance.incrementNumberOfRevisions();
        // add the author for both instances
        dataset.get(idx).addAuthor(author.getName());
        newInstance.addAuthor(author.getName());
        dataset.add(newInstance);
    }

    private void handleDelete(DiffEntry entry, String release) {
        // remove only if there is an instance with the same release
        int idx = getLatestInstanceByName(entry.getNewPath());
        if (idx < 0)
            return;
        if (this.dataset.get(idx).getVersion().equals(release))
            dataset.remove(idx);

        // no stat calculation is needed because the instance will be removed from the dataset
    }

    private void handleModify(DiffEntry entry, String release, RevCommit commit) {
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

        // Get bugs in order to detect if the instance is BUGGY
        List<Bug> commitBugs = getBugsOfCommit(this.bugs, commit);
        Set<String> affectedVersions = new HashSet<>();
        for (Bug bug : commitBugs) {
            affectedVersions.addAll(bug.getAffectedVersions());
        }

        int idx;
        // Set buggy true
        for (String av : affectedVersions) {
            idx = getLatestInstanceByNameAndRelease(entry.getNewPath(), av);
            if (idx >= 0)
                this.dataset.get(idx).setBuggy(true);
            // Check also for previous names of the file (handled with RENAME commits)
            for (String prevName : instance.getPreviousNames()) {
                idx = getLatestInstanceByNameAndRelease(prevName, av);
                if (idx >= 0)
                    this.dataset.get(idx).setBuggy(true);
            }
        }

        //increment number of revisions
        // it's important to do it before loc changes computation
        instance.incrementNumberOfRevisions();
        computeLocChanges(entry, instance);
        // add author
        instance.addAuthor(commit.getAuthorIdent().getName());
    }

    private void handleRename(DiffEntry entry, String release, RevCommit commit) {
        PersonIdent author = commit.getAuthorIdent();
        int idx = getLatestInstanceByName(entry.getOldPath());
        if (idx < 0)
            return;
        if (dataset.get(idx).getVersion().equals(release)) {
            // if in the same release, change only the name
            dataset.get(idx).addPreviousName(entry.getOldPath());
            dataset.get(idx).setFilename(entry.getNewPath());
            // add author
            dataset.get(idx).addAuthor(author.getName());
            // increment number of revisions
            dataset.get(idx).incrementNumberOfRevisions();
        } else {
            DatasetInstance newInstance = new DatasetInstance(dataset.get(idx), release);
            newInstance.addPreviousName(entry.getOldPath());
            newInstance.setFilename(entry.getNewPath());
            dataset.remove(idx);
            //add new author
            newInstance.addAuthor(author.getName());
            // increment number of revisions
            newInstance.incrementNumberOfRevisions();
            dataset.add(newInstance);
        }


        //TODO: check if there is need to calculate stats
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

    private void cutDatasetInHalf() {
        int index = 0;
        int i = 0;
        Set<String> halfVersions = versionManager.getHalfVersions().keySet();
        for (DatasetInstance instance : this.dataset) {
            if (!halfVersions.contains(instance.getVersion())) {
                // index is the first instance with the version to not be considered
                index = i;
                break;
            }
            i++;
        }
        this.dataset = this.dataset.subList(0, index - 1);
    }


    /* -------------------------------------------------------------------------- FEATURES COMPUTATION ----------------------------------------------------------------*/

    private void computeLocChanges(DiffEntry diff, DatasetInstance instance) {
        DiffFormatter df = gitManager.getDiffFormatter();
        int size = instance.getSize();
        int linesAdded = 0;
        int linesDeleted = 0;
        int currentAdd = 0;
        int currentDelete = 0;
        try {
            List<Edit> edits = df.toFileHeader(diff).toEditList();
            for (Edit edit : edits) {
                switch (edit.getType()) {
                    case INSERT:// new LOC have been inserted
                        currentAdd = edit.getLengthB() - edit.getLengthA();
                        linesAdded += currentAdd;
                        break;

                    case DELETE: // LOC have been deleted
                        currentDelete =  edit.getLengthA() - edit.getLengthB();
                        linesDeleted += currentDelete;
                        break;

                    case REPLACE:
                        //LOCs have been modified
                        if (edit.getLengthA() < edit.getLengthB()) {
                            // the new version is bigger; so we have added lines
                            currentAdd = edit.getLengthB() - edit.getLengthA();
                            linesAdded += currentAdd;
                        } else if (edit.getLengthA() > edit.getLengthB()) {
                            // deleted lines
                            currentDelete = edit.getLengthA() - edit.getLengthB();
                            linesDeleted += currentDelete;
                        }
                        break;

                    default:
                        // do nothing
                        break;
                }
            }
        } catch (IOException e) {
            logger.info("IOException computing LOCs");

        }
        int modifiedLines = linesAdded + linesDeleted;
        int churn = linesAdded - linesDeleted;
        // compute the new size
        size += churn;
        if (size < 0)
            size = 0;
        instance.setSize(size);
        instance.addChurn(churn);
        instance.addLocTouched(modifiedLines);
        instance.addLocAdded(linesAdded);
    }


    private void computeAge(DatasetInstance instance, String release) {
        LocalDate creationDate = instance.getCreationDate();
        LocalDate releaseDate = versionManager.getReleaseDateOfVersion(release);
        if (releaseDate == null) {
            // no release found
            return;
        }
        long numDays = Duration.between(creationDate.atStartOfDay(), releaseDate.atStartOfDay()).toDays();
        if (numDays < 0)
            numDays *= -1;
        int numWeeks = (int) Math.ceil((float) numDays / 7);
        instance.setAge(numWeeks);
    }
}
