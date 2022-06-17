package weka;

public class ClassifierEvaluation {

    private int numTrainingRelease;
    private float percTraining;
    private float percDefectiveTraining;
    private float percDefectiveTesting;
    private final String classifier;
    private boolean balancing;
    private boolean featureSelection;
    private boolean sensitivity;
    private int truePositive;
    private int falsePositive;
    private int trueNegative;
    private int falseNegative;
    private final double precision;
    private final double recall;
    private final double auc;
    private final double kappa;

    public ClassifierEvaluation(String classifier, boolean balancing, boolean featureSelection, boolean sensitivity,
                                double precision, double recall, double auc, double kappa) {
        this.classifier = classifier;
        this.balancing = balancing;
        this.featureSelection = featureSelection;
        this.sensitivity = sensitivity;
        this.precision = precision;
        this.recall = recall;
        this.auc = auc;
        this.kappa = kappa;
    }

    public int getNumTrainingRelease() {
        return numTrainingRelease;
    }

    public void setNumTrainingRelease(int numTrainingRelease) {
        this.numTrainingRelease = numTrainingRelease;
    }

    public float getPercTraining() {
        return percTraining;
    }

    public void setPercTraining(float percTraining) {
        this.percTraining = percTraining;
    }

    public float getPercDefectiveTraining() {
        return percDefectiveTraining;
    }

    public void setPercDefectiveTraining(float percDefectiveTraining) {
        this.percDefectiveTraining = percDefectiveTraining;
    }

    public float getPercDefectiveTesting() {
        return percDefectiveTesting;
    }

    public void setPercDefectiveTesting(float percDefectiveTesting) {
        this.percDefectiveTesting = percDefectiveTesting;
    }

    public boolean isBalancing() {
        return balancing;
    }

    public void setBalancing(boolean balancing) {
        this.balancing = balancing;
    }

    public boolean isFeatureSelection() {
        return featureSelection;
    }

    public void setFeatureSelection(boolean featureSelection) {
        this.featureSelection = featureSelection;
    }

    public boolean isSensitivity() {
        return sensitivity;
    }

    public void setSensitivity(boolean sensitivity) {
        this.sensitivity = sensitivity;
    }

    public int getTruePositive() {
        return truePositive;
    }

    public void setTruePositive(int truePositive) {
        this.truePositive = truePositive;
    }

    public int getFalsePositive() {
        return falsePositive;
    }

    public void setFalsePositive(int falsePositive) {
        this.falsePositive = falsePositive;
    }

    public int getTrueNegative() {
        return trueNegative;
    }

    public void setTrueNegative(int trueNegative) {
        this.trueNegative = trueNegative;
    }

    public int getFalseNegative() {
        return falseNegative;
    }

    public void setFalseNegative(int falseNegative) {
        this.falseNegative = falseNegative;
    }

    public String getClassifier(){
        return classifier;
    }

    public double getPrecision() {
        return precision;
    }

    public double getRecall() {
        return recall;
    }

    public double getAuc() {
        return auc;
    }

    public double getKappa() {
        return kappa;
    }


    public String[] toStringArray(String datasetName){
        return new String[]{
                datasetName,
                Integer.toString(numTrainingRelease),
                String.format("%.2f", percTraining * 100),
                String.format("%.2f", percDefectiveTraining * 100),
                String.format("%.2f", percDefectiveTesting * 100),
                classifier,
                Boolean.toString(balancing),
                Boolean.toString(featureSelection),
                Boolean.toString(sensitivity),
                Integer.toString(truePositive),
                Integer.toString(falsePositive),
                Integer.toString(trueNegative),
                Integer.toString(falseNegative),
                String.format("%.6f", precision),
                String.format("%.6f", recall),
                String.format("%.6f", auc),
                String.format("%.6f", kappa)
        };
    }
}
