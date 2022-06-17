package weka;

import logging.LoggerSingleton;
import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.SpreadSubsample;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WekaClassifierEvaluator {

    private final List<ClassifierType> classifiers = Arrays.asList(ClassifierType.values());

    public List<ClassifierEvaluation> evaluateClassifiers(String trainingSet, String testingSet) throws Exception {
        //load datasets
        Instances training;
        Instances testing;
        try (InputStream streamTraining = new FileInputStream(trainingSet);
             InputStream streamTesting = new FileInputStream(testingSet)) {

            ConverterUtils.DataSource sourceTraining = new ConverterUtils.DataSource(streamTraining);
            training = sourceTraining.getDataSet();
            ConverterUtils.DataSource sourceTesting = new ConverterUtils.DataSource(streamTesting);
            testing = sourceTesting.getDataSet();
        }

        /**
         * Perform FEATURE SELECTION
         * using Backward Search greedy algorithm, because the number of
         * features is relatively small and so it does not cost so much in terms of
         * execution time. Moreover, the main goal is to eliminate redundant features.
         */
        AttributeSelection filter = new AttributeSelection();
        CfsSubsetEval evaluator = new CfsSubsetEval();
        // algorithm
        GreedyStepwise search = new GreedyStepwise();
        search.setSearchBackwards(true);

        filter.setEvaluator(evaluator);
        filter.setSearch(search);

        filter.setInputFormat(training);
        Instances trainingFiltered = Filter.useFilter(training, filter);
        List<String> oldAttributes = new ArrayList<>();
        for (int i = 0; i < training.numAttributes(); i++) {
            oldAttributes.add(training.attribute(i).name());
        }
        List<String> filteredAttributes = new ArrayList<>();
        for (int i = 0; i < trainingFiltered.numAttributes(); i++) {
            filteredAttributes.add(trainingFiltered.attribute(i).name());
        }

        LoggerSingleton.getInstance().getLogger().info("Original features: " + training.numAttributes() + " Filtered features: " + trainingFiltered.numAttributes());
        for (String feature : oldAttributes) {
            if (!filteredAttributes.contains(feature)) {
                LoggerSingleton.getInstance().getLogger().info("Removed feature: " + feature);
            }
        }


        // apply filter also on testing set
        // but with the same filtering of the training set, otherwise we could have different results!
        Instances testingFiltered = Filter.useFilter(testing, filter);

        int numAttr = trainingFiltered.numAttributes();
        // setting the last attribute as the attribute to estimate
        trainingFiltered.setClassIndex(numAttr - 1);
        testingFiltered.setClassIndex(numAttr - 1);

        AbstractClassifier classifier;


        /**
         * BALANCING UNDER-SAMPLING
         */
        List<ClassifierEvaluation> classifierEvaluations = new ArrayList<>();
        for (ClassifierType classifierName : this.classifiers) {
            classifier = handleClassifier(classifierName);

            // to do sampling
            FilteredClassifier filteredClassifier = new FilteredClassifier();
            SpreadSubsample spreadSubsample = new SpreadSubsample();
            String[] options = new String[]{"-M", "1.0"};
            spreadSubsample.setOptions(options);

            filteredClassifier.setClassifier(classifier);
            // Under-sampling
            filteredClassifier.setFilter(spreadSubsample);


            if (classifier != null)
                filteredClassifier.buildClassifier(trainingFiltered);

            Evaluation eval = new Evaluation(testingFiltered);

            eval.evaluateModel(filteredClassifier, testingFiltered);

            ClassifierEvaluation record = new ClassifierEvaluation(
                    classifierName.toString(),
                    true,
                    true,
                    false,
                    eval.precision(1),
                    eval.recall(1),
                    eval.areaUnderROC(1),
                    eval.kappa());
            record.setTruePositive((int) eval.numTruePositives(1));
            record.setFalsePositive((int) eval.numFalsePositives(1));
            record.setTrueNegative((int) eval.numTrueNegatives(1));
            record.setFalseNegative((int) eval.numFalseNegatives(1));
            classifierEvaluations.add(record);

        }
        return classifierEvaluations;
    }

    private AbstractClassifier handleClassifier(ClassifierType classifierType) {

        switch (classifierType) {
            case NAIVE_BAYES:
                return new NaiveBayes();

            case RANDOM_FOREST:
                return new RandomForest();

            case IBK:
                return new IBk();

            default:
                LoggerSingleton.getInstance().getLogger().info("Invalid classifier chosen");
                return null;

        }
    }
}
