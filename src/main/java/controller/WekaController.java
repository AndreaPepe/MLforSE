package controller;

import logging.LoggerSingleton;
import model.DatasetInstance;
import weka.ArffGenerator;
import weka.ClassifierEvaluation;
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

    public WekaController(String projectName, List<DatasetInstance> dataset, String[] datasetHeader) {
        this.projectName = projectName;
        this.datasetPerRelease = divideDatasetPerRelease(dataset);
        this.datasetHeader = datasetHeader;
        this.numReleases = datasetPerRelease.keySet().size();
        this.releases = new ArrayList<>(datasetPerRelease.keySet());
    }

    /**
     * This method implements the Walk Forward technique
     * to evaluate different classifiers on a given dataset.
     */
    public List<ClassifierEvaluation> walkForward() {
        List<ClassifierEvaluation> evaluations = new ArrayList<>();
        List<DatasetInstance> trainingSet;
        List<DatasetInstance> testingSet;
        int trainingSetSize;
        String relationName;
        List<ClassifierEvaluation> evaluationToBeAdded;

        WekaClassifierEvaluator evaluator = new WekaClassifierEvaluator();

        for (int i = 1; i < numReleases - 1; i++) {
            trainingSetSize = i;
            trainingSet = buildTrainingSet(i - 1);
            testingSet = this.datasetPerRelease.get(releases.get(i));
            evaluationToBeAdded = new ArrayList<>();
            // generate arff files for training and testing
            try {
                relationName = this.projectName + "_training";
                ArffGenerator.generateArffFromDataset(datasetHeader, trainingSet, relationName, TRAINING_OUTPUT);

                relationName = this.projectName + "_testing";
                ArffGenerator.generateArffFromDataset(datasetHeader, testingSet, relationName, TESTING_OUTPUT);
                evaluationToBeAdded.addAll(evaluator.evaluateClassifiers(TRAINING_OUTPUT, TESTING_OUTPUT));
                for (ClassifierEvaluation ce : evaluationToBeAdded) {
                    ce.setTrainingSetSize(trainingSetSize);
                }
                evaluations.addAll(evaluationToBeAdded);
            } catch (IOException e) {
                LoggerSingleton.getInstance().getLogger().log(Level.SEVERE, "Error generating arff files");
            } catch (Exception e) {
                LoggerSingleton.getInstance().getLogger().log(Level.SEVERE, "Error evaluating classifiers");
            }
        }

        return evaluations;
    }

    private List<DatasetInstance> buildTrainingSet(int indexOfLatestRelease) {
        String release;
        List<DatasetInstance> ret = new ArrayList<>();
        for (int i = 0; i <= indexOfLatestRelease; i++) {
            release = this.releases.get(i);
            ret.addAll(this.datasetPerRelease.get(release));
        }
        return ret;
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
}
