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

    public DatasetInstance(String version, String filename, LocalDate creationDate, boolean buggy) {
        this.version = version;
        this.filename = filename;
        this.authors = new HashSet<>();
        this.creationDate = creationDate;
        this.buggy = buggy;
        this.previousNames = new HashSet<>();
    }

    public DatasetInstance(DatasetInstance old, String newRelease){
        this.version = newRelease;
        this.filename = old.getFilename();
        this.creationDate = old.getCreationDate();
        this.buggy = old.isBuggy();
        this.authors = old.getAuthors();
        this.previousNames = old.getPreviousNames();
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

    public LocalDate getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDate creationDate) {
        this.creationDate = creationDate;
    }

    public Set<String> getPreviousNames() {
        return previousNames;
    }

    public void setPreviousNames(Set<String> previousNames) {
        this.previousNames = previousNames;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getLocTouched() {
        return locTouched;
    }

    public void setLocTouched(int locTouched) {
        this.locTouched = locTouched;
    }

    public int getNumberOfRevisions() {
        return numberOfRevisions;
    }

    public void setNumberOfRevisions(int numberOfRevisions) {
        this.numberOfRevisions = numberOfRevisions;
    }

    public int getNumberOfFixedBugs() {
        return numberOfFixedBugs;
    }

    public void setNumberOfFixedBugs(int numberOfFixedBugs) {
        this.numberOfFixedBugs = numberOfFixedBugs;
    }

    public Set<String> getAuthors() {
        return authors;
    }

    public void setAuthors(Set<String> authors) {
        this.authors = authors;
    }

    public void addAuthor(String author){
        this.authors.add(author);
    }

    public int getLocAdded() {
        return locAdded;
    }

    public void setLocAdded(int locAdded) {
        this.locAdded = locAdded;
    }

    public int getMaxLocAdded() {
        return maxLocAdded;
    }

    public void setMaxLocAdded(int maxLocAdded) {
        this.maxLocAdded = maxLocAdded;
    }

    public float getAvgLocAdded() {
        return avgLocAdded;
    }

    public void setAvgLocAdded(float avgLocAdded) {
        this.avgLocAdded = avgLocAdded;
    }

    public int getChurn() {
        return churn;
    }

    public void setChurn(int churn) {
        this.churn = churn;
    }

    public int getMaxChurn() {
        return maxChurn;
    }

    public void setMaxChurn(int maxChurn) {
        this.maxChurn = maxChurn;
    }

    public float getAvgChurn() {
        return avgChurn;
    }

    public void setAvgChurn(float avgChurn) {
        this.avgChurn = avgChurn;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public void addPreviousName(String name){
        this.previousNames.add(name);
    }
}
