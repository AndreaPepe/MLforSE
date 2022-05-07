package controller;

import exceptions.CommitWithNoReleaseException;
import git.GitSingleton;
import model.Bug;
import model.DatasetInstance;
import model.GitCommit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GitManager {

    private final Git git;
    private final VersionManager versionManager;
    private List<Bug> bugs;

    public GitManager(VersionManager versionManager, List<Bug> bugs) {
        this.git = GitSingleton.getInstance().getGit();
        this.versionManager = versionManager;
        this.bugs = bugs;
    }

    private List<DiffEntry> makeDiff(RevCommit previous, RevCommit current) throws IOException {
        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(git.getRepository());
        df.setDiffComparator(RawTextComparator.DEFAULT);
        // filter only java files
        df.setPathFilter(PathSuffixFilter.create(".java"));
        // set the detection of renamed files
        df.setDetectRenames(true);

        List<DiffEntry> entries;
        if (previous == null) {
            // the previous commit doesn't exist, so the newCommit is the first one
            AbstractTreeIterator oldTreeIterator = new EmptyTreeIterator();
            ObjectReader reader = git.getRepository().newObjectReader();
            AbstractTreeIterator newTreeIterator = new CanonicalTreeParser(null, reader, current.getTree());

            entries = df.scan(oldTreeIterator, newTreeIterator);

        } else {
            entries = df.scan(previous.getTree(), current.getTree());
        }

        // remove test files
        List<DiffEntry> entriesWithNoTest = new ArrayList<>(entries);
        entriesWithNoTest.removeIf(entry -> entry.getNewPath().contains("/test") || entry.getOldPath().contains("/test"));
        return entriesWithNoTest;

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
        List<DatasetInstance> dataset = new ArrayList<>();
        RevCommit prev = null;
        for (Map.Entry<String, List<RevCommit>> release: gitLog.entrySet()){
            // for each release
            for (RevCommit current: release.getValue()){
                // for each commit
                dataset.addAll(analyzeCommitPair(release.getKey(), prev, current));

                prev = current;
            }
        }

        return dataset;
    }

    private List<DatasetInstance> analyzeCommitPair(String release, RevCommit prev, RevCommit current) throws IOException {
        List<DiffEntry> diffs = makeDiff(prev, current);
        List<DatasetInstance> instances = new ArrayList<>();
        boolean isFixCommit = isFixCommit(current);
        for (DiffEntry diff: diffs){
            switch (diff.getChangeType()){
                case ADD -> handleAdd(diff, release, instances);
                case COPY -> handleCopy(diff, release, instances);
                case DELETE -> handleDelete(diff, release, instances);
                case MODIFY -> handleModify(diff, release, instances, isFixCommit);
                case RENAME -> handleRename(diff, release, instances);
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


    private RevCommit findPreviousCommit(RevCommit commit, List<RevCommit> list) {
        RevCommit prev = null;
        for (RevCommit current : list) {
            if (current.getName().equals(commit.getName())) {
                return prev;
            }
            prev = current;
        }
        return prev;
    }
       /* RevWalk revWalk = new RevWalk(git.getRepository());
        // sort commit by ascending date
        revWalk.sort(RevSort.COMMIT_TIME_DESC);
        revWalk.sort(RevSort.REVERSE);
        RevCommit prev = null;
        try {
            RevCommit current = revWalk.next();
            while (current != null) {
                if (current.equals(commit)) {
                    break;
                }
                prev = current;
                current = revWalk.next();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return prev;
      }*/



    private void handleAdd(DiffEntry entry, String release, List<DatasetInstance> list) {
        list.add(new DatasetInstance(release, entry.getNewPath(), false));
    }

    private void handleCopy(DiffEntry entry, String release, List<DatasetInstance> list) {
        // copy an existing file to a new location maintaining the content
        // let's add the new file, but we must also transfer buggyness of the existing file
        String oldName = entry.getOldPath();
        String newName = entry.getNewPath();
        boolean buggy = false; // by default
        for (DatasetInstance csvEntry : list) {
            if (csvEntry.getFilename().equals(oldName)) {
                // the old file was already in the list, so let's take it's buggyness
                buggy = csvEntry.isBuggy();
            }
        }
        list.add(new DatasetInstance(release, newName, buggy));
    }

    private void handleDelete(DiffEntry entry, String release, List<DatasetInstance> list) {
        list.removeIf(csvEntry -> entry.getNewPath().equals(csvEntry.getFilename()));
    }

    private void handleModify(DiffEntry entry, String release, List<DatasetInstance> list, boolean isFixCommit) {
        DatasetInstance foundEntryWithSameRelease = null;
        for (DatasetInstance instance : list) {
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
            list.add(new DatasetInstance(release, entry.getNewPath(), true));
        }

    }

    private void handleRename(DiffEntry entry, String release, List<DatasetInstance> list) {
        for (DatasetInstance csvEntry : list) {
            if (csvEntry.getFilename().equals(entry.getOldPath())) {
                if (csvEntry.getVersion().equals(release)) {
                    // change the path name
                    csvEntry.setFilename(entry.getNewPath());
                } else if (versionManager.isAfterVersion(release, csvEntry.getVersion())) {
                    // if the actual release is after the previous release present in the list, I have to add a new entry
                    // the buggy attribute is inherited
                    list.add(new DatasetInstance(release, entry.getNewPath(), csvEntry.isBuggy()));
                }
            }
        }
    }


}
