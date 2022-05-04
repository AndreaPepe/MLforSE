package model;

import org.eclipse.jgit.revwalk.RevCommit;

public class GitCommit {
    private RevCommit revCommit;
    private String id;
    private String shortId;
    private String date;
    private String author;
    private String msg;
    private String jiraTicket;


    public GitCommit(RevCommit revCommit, String id, String shortId, String date, String author, String msg, String jiraTicket) {
        this.revCommit = revCommit;
        this.id = id;
        this.shortId = shortId;
        this.date = date;
        this.author = author;
        this.msg = msg;
        this.jiraTicket = jiraTicket;
    }

    public GitCommit(RevCommit revCommit, String id, String date, String author, String msg) {
        this.revCommit = revCommit;
        this.id = id;
        this.date = date;
        this.author = author;
        this.msg = msg;
    }
    public RevCommit getRevCommit() {
        return revCommit;
    }

    public void setRevCommit(RevCommit revCommit) {
        this.revCommit = revCommit;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getShortId() {
        return shortId;
    }

    public void setShortId(String shortId) {
        this.shortId = shortId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getJiraTicket() {
        return jiraTicket;
    }

    public void setJiraTicket(String jiraTicket) {
        this.jiraTicket = jiraTicket;
    }

    @Override
    public String toString() {
        return "GitCommit{" +
                "id='" + id + '\'' +
                ", shortId='" + shortId + '\'' +
                ", date='" + date + '\'' +
                ", author='" + author + '\'' +
                ", msg='" + msg + '\'' +
                ", jiraTicket='" + jiraTicket + '\'' +
                '}';
    }


}
