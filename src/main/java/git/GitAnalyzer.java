package git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.List;

public class GitAnalyzer {

    public Iterable<RevCommit> getGitLog(Git git) throws GitAPIException {
        return git.log().call();
    }

    public List<RevCommit> getCommitsContainingString(Iterable<RevCommit> commits, String target){
        ArrayList<RevCommit> results = new ArrayList<>();
        for (RevCommit commit : commits) {
            // comparison is done without lower case
            if (commit.getFullMessage().contains(target))
                results.add(commit);
        }

        return results;
    }
}
