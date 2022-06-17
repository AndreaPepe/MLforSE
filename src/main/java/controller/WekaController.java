package controller;

import logging.LoggerSingleton;
import model.DatasetInstance;
import weka.ArffGenerator;
import weka.ClassifierEvaluation;
import weka.CostSensitivity;
import weka.WekaClassifierEvaluator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class WekaController {

    private static final String TESTING_OUTPUT = "testing.arff";
    private static final String TRAINING_OUTPUT = "training.arff";
    private final String projectName;
    private final Map<String, List<DatasetInstance>> datasetPerRelease;
    private final String[] datasetHeader;
    private final int numReleases;
    private final List<String> releases;

    private final Map<String, List<DatasetInstance>> datasetsWithSnoring;

    public WekaController(String projectName, Map<String, List<DatasetInstance>> datasetsWithSnoring, String[] datasetHeader) {
        this.projectName = projectName;
        this.datasetsWithSnoring = datasetsWithSnoring;
        this.datasetHeader = datasetHeader;
        this.numReleases = datasetsWithSnoring.size();
        this.releases = new ArrayList<>(datasetsWithSnoring.keySet());

        // the last dataset is the one from which select testing sets
        this.datasetPerRelease = divideDatasetPerRelease(datasetsWithSnoring.get(releases.get(releases.size() - 1)));
    }

    /**
     * This method applies the Walk Forward validation technique
     * in order to estimate quality metrics of the built model.
     * It takes in account the presence of the Snoring effect, using
     * training sets that are not affected by information known after
     * the date of the release they represent.
     *
     * @return A list of record of evaluations of classifiers
     */
    public List<ClassifierEvaluation> walkForwardWithSnoring(CostSensitivity costSensitivity) {
        List<ClassifierEvaluation> evaluations = new ArrayList<>();
        List<DatasetInstance> trainingSet;
        List<DatasetInstance> testingSet;
        int trainingSetSize;
        float percTraining;
        float percDefectiveTraining;
        float percDefectiveTesting;
        String relationName;
        List<ClassifierEvaluation> evaluationToBeAdded;

        WekaClassifierEvaluator evaluator = new WekaClassifierEvaluator();

        // The index i is always the index of the TESTING set in this loop
        for (int i = 1; i < numReleases - 1; i++) {
            trainingSetSize = i;
            String currentReleaseForTraining = releases.get(i - 1);
            trainingSet = this.datasetsWithSnoring.get(currentReleaseForTraining);
            testingSet = this.datasetPerRelease.get(releases.get(i));

            Float[] percentages = computePercentagesOnDatasets(trainingSet, testingSet);
            percTraining = percentages[0];
            percDefectiveTraining = percentages[1];
            percDefectiveTesting = percentages[2];

            evaluationToBeAdded = new ArrayList<>();
            // generate arff files for training and testing
            try {
                relationName = this.projectName + "_training";
                ArffGenerator.generateArffFromDataset(datasetHeader, trainingSet, relationName, TRAINING_OUTPUT);

                relationName = this.projectName + "_testing";
                ArffGenerator.generateArffFromDataset(datasetHeader, testingSet, relationName, TESTING_OUTPUT);
                evaluationToBeAdded.addAll(evaluator.evaluateClassifiers(TRAINING_OUTPUT, TESTING_OUTPUT, costSensitivity));
                for (ClassifierEvaluation ce : evaluationToBeAdded) {
                    ce.setNumTrainingRelease(trainingSetSize);
                    ce.setPercTraining(percTraining);
                    ce.setPercDefectiveTraining(percDefectiveTraining);
                    ce.setPercDefectiveTesting(percDefectiveTesting);
                }
                evaluations.addAll(evaluationToBeAdded);
            } catch (IOException e) {
                LoggerSingleton.getInstance().getLogger().log(Level.SEVERE, "Error generating arff files");
            } catch (Exception e) {
                LoggerSingleton.getInstance().getLogger().log(Level.SEVERE, "Error evaluating classifiers");
                e.printStackTrace();
            }
        }

        return evaluations;
    }



    private Map<String, List<DatasetInstance>> divideDatasetPerRelease(List<DatasetInstance> dataset) {
        Map<String, List<DatasetInstance>> result = new LinkedHashMap<>();
        for (DatasetInstance instance : dataset) {
            if (!result.containsKey(instance.getVersion())) {
                // create a new entry in the hash map
                result.put(instance.getVersion(), new ArrayList<>());
            }
            result.get(instance.getVersion()).add(instance);
        }
        return result;
    }

    private Float[] computePercentagesOnDatasets(List<DatasetInstance> trainingSet, List<DatasetInstance> testingSet){
        int numRecordTraining = trainingSet.size();
        int numRecordTesting = testingSet.size();
        float percTraining = (float) numRecordTraining / (numRecordTraining + numRecordTesting);
        int defectiveTraining = 0;
        int defectiveTesting = 0;

        for (DatasetInstance i : trainingSet){
            if (i.isBuggy())
                defectiveTraining++;
        }

        for (DatasetInstance i : testingSet){
            if (i.isBuggy())
                defectiveTesting++;
        }

        float percDefectiveTraining = (float) defectiveTraining / numRecordTraining;
        float percDefectiveTesting = (float) defectiveTesting / numRecordTesting;

        return new Float[] {percTraining, percDefectiveTraining, percDefectiveTesting};
    }
}
