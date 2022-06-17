package weka;

import logging.LoggerSingleton;
import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.CostMatrix;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.CostSensitiveClassifier;
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

    // false negatives cost 10 times false positives
    private final Double CFP = 1.0;
    private final Double CFN = 10 * CFP;

    private final List<ClassifierType> classifiers = Arrays.asList(ClassifierType.values());

    public List<ClassifierEvaluation> evaluateClassifiers(String trainingSet, String testingSet, CostSensitivity costSensitivity) throws Exception {
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

            Evaluation eval;
            /**
             * COST SENSITIVITY
             */
            if (costSensitivity == CostSensitivity.NO_COST_SENSITIVITY) {
                eval = new Evaluation(testingFiltered);
                eval.evaluateModel(filteredClassifier, testingFiltered);
            } else if (costSensitivity == CostSensitivity.SENSITIVE_THRESHOLD) {
                CostSensitiveClassifier csc = new CostSensitiveClassifier();
                // sensitive threshold has minimizeExpectedCost equals to true
                csc.setMinimizeExpectedCost(true);
                csc.setClassifier(filteredClassifier);

                CostMatrix costMatrix = createCostMatrix(CFP, CFN);
                csc.setCostMatrix(costMatrix);
                csc.buildClassifier(trainingFiltered);
                eval = new Evaluation(testingFiltered, csc.getCostMatrix());
                eval.evaluateModel(csc, testingFiltered);
            } else {
                // SENSITIVE LEARNING (minimizeExpectedCost = false)
                CostSensitiveClassifier csc = new CostSensitiveClassifier();
                csc.setMinimizeExpectedCost(false);
                csc.setClassifier(filteredClassifier);

                CostMatrix costMatrix = createCostMatrix(CFP, CFN);
                csc.setCostMatrix(costMatrix);
                csc.buildClassifier(trainingFiltered);
                eval = new Evaluation(testingFiltered, csc.getCostMatrix());
                eval.evaluateModel(csc, testingFiltered);
            }

            boolean usedSensitivity = costSensitivity != CostSensitivity.NO_COST_SENSITIVITY;
            ClassifierEvaluation record = new ClassifierEvaluation(
                    classifierName.toString(),
                    true,
                    true,
                    usedSensitivity);
            record.setPrecision(eval.precision(1));
            record.setRecall(eval.recall(1));
            record.setAuc(eval.areaUnderROC(1));
            record.setKappa(eval.kappa());
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

    private CostMatrix createCostMatrix(Double costFP, Double costFN) {
        CostMatrix costMatrix = new CostMatrix(2);
        costMatrix.setCell(0, 0, 0.0);
        costMatrix.setCell(1, 0, costFP);
        costMatrix.setCell(0, 1, costFN);
        costMatrix.setCell(1, 1, 0.0);
        return costMatrix;
    }
}
