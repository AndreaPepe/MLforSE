package git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GitAnalyzer {

    public Iterable<RevCommit> getGitLog(Git git, Date maxDate) throws GitAPIException {
        return git.log().setRevFilter(CommitTimeRevFilter.before(maxDate)).call();
    }

    public List<RevCommit> getDatetimeSortedGitLog(Git git, Date maxDate) throws GitAPIException {
        Iterable<RevCommit> gitLog = getGitLog(git, maxDate);
        ArrayList<RevCommit> allLog = new ArrayList<>();
        for (RevCommit c : gitLog)
            allLog.add(c);

        allLog.sort((o1, o2) -> {
            if (o1.getCommitTime() > o2.getCommitTime())
                return 1;
            else if (o1.getCommitTime() == o2.getCommitTime())
                return 0;
            return -1;
        });
        return allLog;
    }

    public List<RevCommit> getCommitsContainingString(Iterable<RevCommit> commits, String target) {
        ArrayList<RevCommit> results = new ArrayList<>();
        for (RevCommit commit : commits) {
            // comparison is done without lower case
            if (commit.getFullMessage().contains(target))
                results.add(commit);
        }

        return results;
    }
}
