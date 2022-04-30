package model;

import java.util.ArrayList;
import java.util.List;

public class Bug {
    private final JiraTicket ticket;
    private final String openingDate;
    private String openingVersion;
    private final String fixDate;
    private String fixVersion;

    private GitCommit fixCommit;
    private List<GitCommit> otherCommits;

    private List<String> affectedVersions;
    private String injectedVersion;


    public Bug(JiraTicket ticket, String openingDate, String fixDate, List<String> affectedVersions) {
        this.ticket = ticket;
        this.openingDate = openingDate;
        this.fixDate = fixDate;
        this.affectedVersions = affectedVersions;
    }

    public Bug(JiraTicket ticket) {
        this.ticket = ticket;
        this.openingDate = ticket.getCreationDate();
        this.fixDate = ticket.getFixedDate();
        this.affectedVersions = ticket.getAffectedVersions();
    }

    public JiraTicket getTicket() {
        return ticket;
    }

    public String getOpeningDate() {
        return openingDate;
    }

    public String getOpeningVersion() {
        return openingVersion;
    }

    public void setOpeningVersion(String openingVersion) {
        this.openingVersion = openingVersion;
    }

    public String getFixDate() {
        return fixDate;
    }

    public String getFixVersion() {
        return fixVersion;
    }

    public void setFixVersion(String fixVersion) {
        this.fixVersion = fixVersion;
    }

    public GitCommit getFixCommit() {
        return fixCommit;
    }

    public void setFixCommit(GitCommit fixCommit) {
        this.fixCommit = fixCommit;
    }

    public List<GitCommit> getOtherCommits() {
        return otherCommits;
    }

    public void setOtherCommits(List<GitCommit> otherCommits) {
        this.otherCommits = otherCommits;
    }

    public List<String> getAffectedVersions() {
        return affectedVersions;
    }

    public void setAffectedVersions(List<String> affectedVersions) {
        this.affectedVersions = affectedVersions;
    }

    public String getInjectedVersion() {
        return injectedVersion;
    }

    public void setInjectedVersion(String injectedVersion) {
        this.injectedVersion = injectedVersion;
    }

    public void addOtherCommit(GitCommit commit) {
        if (this.otherCommits == null)
            this.otherCommits = new ArrayList<>();
        this.otherCommits.add(commit);
        // maybe add automatic set of most recent commit
    }
}
