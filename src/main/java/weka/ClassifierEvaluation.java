package weka;

public class ClassifierEvaluation {

    private final String classifier;
    private final double precision;
    private final double recall;
    private final double auc;
    private final double kappa;

    private int trainingSetSize;

    public ClassifierEvaluation(String classifier, double precision, double recall, double auc, double kappa) {
        this.classifier = classifier;
        this.precision = precision;
        this.recall = recall;
        this.auc = auc;
        this.kappa = kappa;
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

    public void setTrainingSetSize(int size){
        this.trainingSetSize = size;
    }

    public int getTrainingSetSize(){
        return trainingSetSize;
    }

    public String[] toStringArray(String datasetName){
        return new String[]{
                datasetName,
                Integer.toString(trainingSetSize),
                classifier,
                Double.toString(precision),
                Double.toString(recall),
                Double.toString(auc),
                Double.toString(kappa)
        };
    }
}
