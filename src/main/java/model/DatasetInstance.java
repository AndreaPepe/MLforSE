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
    private Set<String> authors;                // set of authors that worked on the file
    private int locAdded;                       // number of LOC added over revisions
    private int maxLocAdded;                    // maximum number of loc added in a revision
    private float avgLocAdded;                  // average number of loc added over revisions
    private int churn;                          // sum of (added - deleted) LOC over revisions
    private int maxChurn;                       // maximum number of churn in a revision
    private float avgChurn;                     // average number of churn over revisions
    private Set<String> fixedBugs;              // set of fixed bugs, used to calculate the number of fixed bugs
    private int age;                            // age of the file in weeks

    private int historicalLocTouched;           // number of LOC touched in the history of the file, used to compute the Weighted Age
    private int historicalNumberOfRevisions;    // number of total commits that touched the file from its existence


    public DatasetInstance(String version, String filename, LocalDate creationDate, boolean buggy) {
        this.version = version;
        this.filename = filename;
        this.authors = new HashSet<>();
        this.creationDate = creationDate;
        this.previousNames = new HashSet<>();
        this.historicalLocTouched = 0;
        this.historicalNumberOfRevisions = 0;

        // features
        this.size = 0;
        this.locTouched = 0;
        this.locAdded = 0;
        this.maxLocAdded = 0;
        this.avgLocAdded = 0f;
        this.numberOfRevisions = 0;
        this.churn = 0;
        this.maxChurn = 0;
        this.avgChurn = 0f;
        this.age = 0;
        this.fixedBugs = new HashSet<>();
        this.buggy = buggy;
    }

    /**
     * This constructor is used to clone an instance for backup purposes
     * @param instance the instance to be cloned
     */
    public DatasetInstance(DatasetInstance instance){
        this.version = instance.getVersion();

        this.filename = instance.getFilename();
        this.creationDate = instance.getCreationDate();
        this.authors = instance.getAuthors();
        this.previousNames = instance.getPreviousNames();
        this.historicalLocTouched = instance.getHistoricalLocTouched();
        this.historicalNumberOfRevisions = instance.getHistoricalNumberOfRevisions();

        // features
        this.size = instance.getSize();
        this.locTouched = instance.getLocTouched();
        this.locAdded = instance.getLocAdded();
        this.maxLocAdded = instance.getMaxLocAdded();
        this.avgLocAdded = instance.getAvgLocAdded();
        this.numberOfRevisions = instance.getNumberOfRevisions();
        this.churn = instance.getChurn();
        this.maxChurn = instance.getMaxChurn();
        this.avgChurn = instance.getAvgChurn();
        this.age = instance.getAge();
        this.fixedBugs = instance.getFixedBugs();
        this.buggy = instance.isBuggy();
    }

    public DatasetInstance(DatasetInstance old, String newRelease) {
        // this is the only change
        this.version = newRelease;

        this.filename = old.getFilename();
        this.creationDate = old.getCreationDate();
        this.authors = old.getAuthors();
        this.previousNames = old.getPreviousNames();
        this.historicalLocTouched = old.getHistoricalLocTouched();
        this.historicalNumberOfRevisions = old.getHistoricalNumberOfRevisions();

        // features
        this.size = old.getSize();
        this.locTouched = 0;
        this.locAdded = 0;
        this.maxLocAdded = old.getMaxLocAdded();
        this.avgLocAdded = old.getAvgLocAdded();
        this.numberOfRevisions = 0;
        this.churn = 0;
        this.maxChurn = old.getMaxChurn();
        this.avgChurn = old.getAvgChurn();
        this.age = old.getAge();
        this.fixedBugs = old.getFixedBugs();
        this.buggy = false;
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

    public Set<String> getAuthors() {
        return authors;
    }

    public void setAuthors(Set<String> authors) {
        this.authors = authors;
    }

    public void addAuthor(String author) {
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

    public Set<String> getFixedBugs() {
        return fixedBugs;
    }

    public void setFixedBugs(Set<String> fixedBugs) {
        this.fixedBugs = fixedBugs;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public int getHistoricalLocTouched(){ return historicalLocTouched;}

    public void setHistoricalLocTouched(int historicalLocTouched){ this.historicalLocTouched = historicalLocTouched;}

    public int getHistoricalNumberOfRevisions(){ return historicalNumberOfRevisions;}

    public void setHistoricalNumberOfRevisions(int historicalNumberOfRevisions){this.historicalNumberOfRevisions = historicalNumberOfRevisions;}

    public void addPreviousName(String oldPath) {
        this.previousNames.add(oldPath);
    }

    public void addChurn(int churn) {
        this.churn += churn;
        this.maxChurn = Math.max(this.maxChurn, churn);
        this.avgChurn = (float) this.churn / this.historicalNumberOfRevisions;
    }

    public void incrementNumberOfRevisions() {
        this.numberOfRevisions++;
        this.historicalNumberOfRevisions++;
    }

    public void addLocTouched(int locTouched) {
        this.locTouched += locTouched;
        this.historicalLocTouched += locTouched;
    }

    public void addLocAdded(int locAdded) {
        this.locAdded += locAdded;
        this.maxLocAdded = Math.max(this.maxLocAdded, locAdded);
        this.avgLocAdded = (float) this.locAdded / this.historicalNumberOfRevisions;
    }

    public void addFixedBug(String bugTicket){
        this.fixedBugs.add(bugTicket);
    }

    public float getWeightedAge(){
        return (float)this.age/this.historicalLocTouched;
    }

    public int getNumberOfAuthors(){
        return this.authors.size();
    }

    public int getNumberOfFixedBugs(){
        return this.fixedBugs.size();
    }

    public String isBuggyYesOrNot(){
        if (this.buggy)
            return "yes";
        else
            return  "no";
    }
    /**
     * This method transforms the DatasetInstance class in an array of Strings,
     * representing an instance in a CSV file dataset.
     * Columns are:
     * - version
     * - filepath
     * - size
     * - LOC touched
     * - LOC added
     * - max LOC added
     * - avg LOC added
     * - number of revisions
     * - number of authors
     * - churn
     * - max churn
     * - avg churn
     * - number of fixed bugs
     * - age
     * - weighted age
     * - buggy {yes, no}
     *
     * @return Array of strings to be inserted in a CSV file
     */
    public String[] toStringArray() {

        return new String[]{
                this.version,
                this.filename,
                Integer.toString(this.size),
                Integer.toString(this.locTouched),
                Integer.toString(this.locAdded),
                Integer.toString(this.maxLocAdded),
                String.format("%f", this.avgLocAdded),
                Integer.toString(this.numberOfRevisions),
                Integer.toString(this.getNumberOfAuthors()),
                Integer.toString(this.churn),
                Integer.toString(this.maxChurn),
                String.format("%f", this.avgChurn),
                Integer.toString(this.getNumberOfFixedBugs()),
                Integer.toString(this.age),
                String.format("%f", this.getWeightedAge()),
                this.isBuggyYesOrNot()
        };
    }


}
