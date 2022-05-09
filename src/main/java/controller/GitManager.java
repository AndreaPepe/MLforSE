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
    private DiffFormatter df;

    public GitManager(Git git) {
        this.git = git;
    }

    public List<DiffEntry> makeDiff(RevCommit previous, RevCommit current) throws IOException {

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

    public DiffFormatter getDiffFormatter(){
        return this.df;
    }

    public void setDiffFormatter(){
        df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(git.getRepository());
        df.setDiffComparator(RawTextComparator.DEFAULT);
        // filter only java files
        df.setPathFilter(PathSuffixFilter.create(".java"));
        // set the detection of renamed files
        df.setDetectRenames(true);
    }

}
