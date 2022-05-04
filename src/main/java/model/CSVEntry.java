package model;

public class CSVEntry {
    private String version;
    private String filename;
    private boolean buggy;

    public CSVEntry(String version, String filename, boolean buggy) {
        this.version = version;
        this.filename = filename;
        this.buggy = buggy;
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

    public String[] toStringArray(){
        return new String[] {this.version, this.filename, String.valueOf(this.buggy)};
    }
}
