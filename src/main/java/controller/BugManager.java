package controller;

import model.Bug;
import model.GitCommit;

import java.time.LocalDate;
import java.util.List;

public class BugManager {

    /**
     * If the commit contains the ticket (retrieved by Jira) of the bug, and if the fix date
     * of Jira corresponds to the date of the commit, then the commit is classified as the fix commit of the bug.
     * Otherwise, the commit is classified as 'otherCommit'.
     *
     * @param bugs
     * @param commits
     * @return The modified list of bugs
     */
    public static List<Bug> setFixCommitAndOtherCommits(List<Bug> bugs, List<GitCommit> commits) {
        String commitDate;
        for (Bug bug : bugs) {
            for (GitCommit commit : commits) {
                if (commit.getMsg().contains(bug.getTicket().getKey())) {
                    commitDate = commit.getDate();
                    if (commitDate.equals(bug.getFixDate())) {
                        bug.setFixCommit(commit);
                    } else {
                        // classify this commit as other commit
                        bug.addOtherCommit(commit);
                    }
                }
            }
        }

        return bugs;
    }

    /**
     * Remove bugs that have no fix commit and no commits that make reference to them
     *
     * @param bugs
     * @return The modified list of bugs
     */
    private static List<Bug> removeBugsWithNoCommits(List<Bug> bugs) {
        bugs.removeIf(bug -> bug.getFixCommit() == null && (bug.getOtherCommits() == null || bug.getOtherCommits().isEmpty()));
        return bugs;
    }

    public static List<Bug> patchFixCommit(List<Bug> bugs) {
        bugs = removeBugsWithNoCommits(bugs);
        for (Bug bug : bugs) {
            if (bug.getFixCommit() == null) {
                // get the other commit with last date
                GitCommit candidateFix = null;
                List<GitCommit> others = bug.getOtherCommits();
                for (GitCommit com : others) {
                    LocalDate comDate = LocalDate.parse(com.getDate());
                    if (candidateFix == null || comDate.isAfter(LocalDate.parse(candidateFix.getDate()))) {
                        candidateFix = com;
                    }
                }
                if (candidateFix == null)
                    bugs.remove(bug);
                else
                    bug.setFixCommit(candidateFix);
            }
        }
        return bugs;
    }
}
