package git;

import model.GitCommit;
import org.eclipse.jgit.revwalk.RevCommit;

import java.text.SimpleDateFormat;
import java.util.Date;

public class GitCommitFactory {
    private static GitCommitFactory instance;

    public static GitCommitFactory getInstance() {
        if (instance == null) {
            instance = new GitCommitFactory();
        }
        return instance;
    }

    protected GitCommitFactory() {
    }

    public GitCommit parseCommit(RevCommit commit, String jiraTicketKey) {
        String id = commit.getId().toString().split(" ")[1];
        String shortId = commit.getId().toString().split(" ")[2];
                        /* Substitute with the method 'getFullMessage()'
                        if you want to read the whole commit message plus other infos */
        String msg = commit.getShortMessage();

        String author = commit.getCommitterIdent().getName();
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(commit.getCommitTime() * 1000L));
        return new GitCommit(id, shortId, date, author, msg, jiraTicketKey);
    }
}
