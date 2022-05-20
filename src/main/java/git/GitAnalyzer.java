package git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            if (isExactlyContained(commit.getFullMessage(), target))
                results.add(commit);
        }

        return results;
    }

    /**
     * This method is used to avoid incorrect results obtained with the
     * contains() method of strings.
     * E.g. if "TICKET-123" is present in the source, and we search for the
     * string "TICKET-1", the result is true with contains(), but is false
     * using the following method.
     *
     * @param source The source text
     * @param target The string to exactly find
     * @return true if the target string is found, false otherwise
     */
    private boolean isExactlyContained(String source, String target){
        String pattern = "\\b" + target + "\\b";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(source);
        return m.find();
    }
}
