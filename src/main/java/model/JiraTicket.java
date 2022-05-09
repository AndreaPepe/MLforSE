package model;

import java.util.List;

public class JiraTicket {
    private String key;
    private String creationDate;
    private String fixedDate;
    private List<String> affectedVersions;

    public JiraTicket(String key, String creationDate, String fixedDate, List<String> affectedVersions) {
        this.key = key;
        this.creationDate = creationDate;
        this.fixedDate = fixedDate;
        this.affectedVersions = affectedVersions;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    public String getFixedDate() {
        return fixedDate;
    }

    public void setFixedDate(String fixedDate) {
        this.fixedDate = fixedDate;
    }

    public List<String> getAffectedVersions() {
        return affectedVersions;
    }

    public void setAffectedVersions(List<String> affectedVersions) {
        this.affectedVersions = affectedVersions;
    }

    @Override
    public String toString() {
        return "JiraTicket{" +
                "key='" + key + '\'' +
                ", creationDate='" + creationDate + '\'' +
                ", fixedDate='" + fixedDate + '\'' +
                ", affectedVersions=" + affectedVersions +
                '}';
    }
}
