package controller;

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

public class GitManager {

    private final Git git;

    public GitManager(Git git) {
        this.git = git;
    }

    public List<DiffEntry> makeDiff(RevCommit previous, RevCommit current) throws IOException {
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


}
