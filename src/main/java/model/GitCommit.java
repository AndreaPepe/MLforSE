package model;

public class GitCommit {
    private String id;
    private String shortId;
    private String date;
    private String author;
    private String msg;
    private String jiraTicket;


    public GitCommit(String id, String shortId, String date, String author, String msg, String jiraTicket) {
        this.id = id;
        this.shortId = shortId;
        this.date = date;
        this.author = author;
        this.msg = msg;
        this.jiraTicket = jiraTicket;
    }

    public GitCommit(String id, String date, String author, String msg) {
        this.id = id;
        this.date = date;
        this.author = author;
        this.msg = msg;
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
