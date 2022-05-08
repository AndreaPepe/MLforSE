package model;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public class DatasetInstance {
    private String version;
    private String filename;
    private boolean buggy;

    private LocalDate creationDate;
    private Set<String> previousNames;

    // features
    private int size;                           // LOC
    private int locTouched;                     // LOC touched (added + deleted + modified)
    private int numberOfRevisions;              // number of commits
    private int numberOfFixedBugs;              // number of bug fixed
    private Set<String> authors;                // set of authors that worked on the file
    private int locAdded;                       // number of LOC added over revisions
    private int maxLocAdded;                    // maximum number of loc added in a revision
    private float avgLocAdded;                  // average number of loc added over revisions
    private int churn;                          // sum of (added - deleted) LOC over revisions
    private int maxChurn;                       // maximum number of churn in a revision
    private float avgChurn;                     // average number of churn over revisions
    private int age;                            // age of the file in weeks

    public DatasetInstance(String version, String filename, String author, LocalDate creationDate, boolean buggy) {
        this.version = version;
        this.filename = filename;
        this.authors = new HashSet<>();
        this.authors.add(author);
        this.creationDate = creationDate;
        this.buggy = buggy;
        this.previousNames = new HashSet<>();
    }


    public DatasetInstance(String version, String filename, boolean buggy) {
        this.version = version;
        this.filename = filename;
        this.buggy = buggy;
        this.previousNames = new HashSet<>();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public boolean isBuggy() {
        return buggy;
    }

    public void setBuggy(boolean buggy) {
        this.buggy = buggy;
    }

    public String[] toStringArray() {
        return new String[]{this.version, this.filename, String.valueOf(this.buggy)};
    }
}
