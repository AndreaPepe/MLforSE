package controller;

import git.GitSingleton;
import model.CSVEntry;
import model.GitCommit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GitManager {

    private Git git;
    private VersionManager versionManager;

    public GitManager(VersionManager versionManager) {
        this.git = GitSingleton.getInstance().getGit();
        this.versionManager = versionManager;
    }

    private RevCommit findPreviousCommit(RevCommit commit, List<RevCommit> list) {
        RevCommit prev = null;
        for(RevCommit current: list){
            if(current.getName().equals(commit.getName())){
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


    private List<DiffEntry> getGitDiff(GitCommit commit, List<RevCommit> list) throws IOException {
        DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
        diffFormatter.setRepository(git.getRepository());
        diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
        // filter only java files
        diffFormatter.setPathFilter(PathSuffixFilter.create(".java"));
        // set the detection of renamed files
        diffFormatter.setDetectRenames(true);

        RevCommit newCommit = commit.getRevCommit();
        RevCommit oldCommit = findPreviousCommit(newCommit, list);

        List<DiffEntry> entries = null;
        if (oldCommit == null) {
            // the previous commit doesn't exist, so the newCommit is the first one
            AbstractTreeIterator oldTreeIterator = new EmptyTreeIterator();
            ObjectReader reader = git.getRepository().newObjectReader();
            AbstractTreeIterator newTreeIterator = new CanonicalTreeParser(null, reader, newCommit.getTree());

            entries = diffFormatter.scan(oldTreeIterator, newTreeIterator);

        } else {
            entries = diffFormatter.scan(oldCommit.getTree(), newCommit.getTree());
        }

        return entries;
    }

    public List<CSVEntry> commitToCSVEntries(GitCommit commit, List<RevCommit> list) throws IOException {
        String commitRelease = versionManager.findVersionByDate(commit.getDate());
        List<DiffEntry> diffs = getGitDiff(commit, list);
        List<CSVEntry> result = new ArrayList<>();
        if (diffs == null) {
            return result;
        }
        for (DiffEntry entry : diffs) {
            switch (entry.getChangeType()) {
                case ADD -> manageAdd(entry, commitRelease, result);
                case COPY -> manageCopy(entry, commitRelease, result);
                case DELETE -> manageDelete(entry, commitRelease, result);
                case MODIFY -> manageModify(entry, commitRelease, result);
                case RENAME -> manageRename(entry, commitRelease, result);
            }
        }
        return result;
    }


    private void manageAdd(DiffEntry entry, String release, List<CSVEntry> list) {
        list.add(new CSVEntry(release, entry.getNewPath(), false));
    }

    private void manageCopy(DiffEntry entry, String release, List<CSVEntry> list) {
        // copy an existing file to a new location maintaining the content
        // let's add the new file, but we must also transfer buggyness of the existing file
        String oldName = entry.getOldPath();
        String newName = entry.getNewPath();
        boolean buggy = false; // by default
        for (CSVEntry csvEntry : list) {
            if (csvEntry.getFilename().equals(oldName)) {
                // the old file was already in the list, so let's take it's buggyness
                buggy = csvEntry.isBuggy();
            }
        }
        list.add(new CSVEntry(release, newName, buggy));
    }

    private void manageDelete(DiffEntry entry, String release, List<CSVEntry> list) {
        list.removeIf(csvEntry -> entry.getNewPath().equals(csvEntry.getFilename()));
    }

    private void manageModify(DiffEntry entry, String release, List<CSVEntry> list) {
        CSVEntry foundEntryWithSameRelease = null;
        for (CSVEntry csvEntry : list) {
            if (csvEntry.getFilename().equals(entry.getNewPath())) {
                // if the file has been modified in that release, set it to buggy
                if (csvEntry.getVersion().equals(release)) {
                    foundEntryWithSameRelease = csvEntry;
                    break;
                }
            }
        }

        if (foundEntryWithSameRelease != null){
            foundEntryWithSameRelease.setBuggy(true);
        }else{
            list.add(new CSVEntry(release, entry.getNewPath(), true));
        }

    }

    private void manageRename(DiffEntry entry, String release, List<CSVEntry> list) {
        for (CSVEntry csvEntry : list) {
            if (csvEntry.getFilename().equals(entry.getOldPath())) {
                if (csvEntry.getVersion().equals(release)) {
                    // change the path name
                    csvEntry.setFilename(entry.getNewPath());
                } else if (versionManager.isAfterVersion(release, csvEntry.getVersion())) {
                    // if the actual release is after the previous release present in the list, I have to add a new entry
                    // the buggy attribute is inherited
                    list.add(new CSVEntry(release, entry.getNewPath(), csvEntry.isBuggy()));
                }
            }
        }
    }
}
