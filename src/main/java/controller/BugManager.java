package controller;

import model.Bug;
import model.GitCommit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BugManager {

    private BugManager() {
    }

    /**
     * If the commit contains the ticket (retrieved by Jira) of the bug, and if the fix date
     * of Jira corresponds to the date of the commit, then the commit is classified as the fix commit of the bug.
     * Otherwise, the commit is classified as 'otherCommit'.
     *
     * @param bugs    list of bugs
     * @param commits list of commits
     */
    public static void setFixCommitAndOtherCommits(List<Bug> bugs, List<GitCommit> commits) {
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

    }

    /**
     * Remove bugs that have no fix commit and no commits that make reference to them
     *
     * @param bugs list of bugs
     */
    private static void removeBugsWithNoCommits(List<Bug> bugs) {
        bugs.removeIf(bug -> bug.getFixCommit() == null && (bug.getOtherCommits() == null || bug.getOtherCommits().isEmpty()));
    }

    public static void patchFixCommit(List<Bug> bugs) {
        removeBugsWithNoCommits(bugs);
        List<Bug> bugsToBeRemoved = new ArrayList<>();
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
                    bugsToBeRemoved.add(bug);
                else
                    bug.setFixCommit(candidateFix);
            }
        }

        removeBugs(bugs, bugsToBeRemoved);
    }

    private static void removeBugs(List<Bug> bugs, List<Bug> toRemove){
        for(Bug bug: toRemove){
            bugs.remove(bug);
        }
    }

    public static void sortBugsChronologically (List<Bug> bugs){
        bugs.sort((bug1, bug2) -> {
            LocalDate bug1Date = LocalDate.parse(bug1.getTicket().getCreationDate());
            LocalDate bug2Date = LocalDate.parse(bug2.getTicket().getCreationDate());

            return bug1Date.compareTo(bug2Date);
        });
    }
}
