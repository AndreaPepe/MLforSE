package weka;

import logging.LoggerSingleton;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.rules.ZeroR;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;

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

        int numAttr = training.numAttributes();
        // setting the last attribute as the attribute to estimate
        training.setClassIndex(numAttr - 1);
        testing.setClassIndex(numAttr - 1);

        AbstractClassifier classifier;
        List<ClassifierEvaluation> classifierEvaluations = new ArrayList<>();
        for (ClassifierType classifierName : this.classifiers) {
            classifier = handleClassifier(classifierName);

            if (classifier != null)
                classifier.buildClassifier(training);

            Evaluation eval = new Evaluation(testing);

            eval.evaluateModel(classifier, testing);

            // TODO: estimate precision also on other metrics
            classifierEvaluations.add(new ClassifierEvaluation(
                    classifierName.toString(),
                    eval.precision(1),
                    eval.recall(1),
                    eval.areaUnderROC(1),
                    eval.kappa()));

        }
        return classifierEvaluations;
    }

    private AbstractClassifier handleClassifier(ClassifierType classifierType) {

        switch (classifierType) {
            case NAIVE_BAYES:
                return new NaiveBayes();

            case J48:
                return new J48();

            case RANDOM_FOREST:
                return new RandomForest();

            case IBK:
                return new IBk();

            case ZERO_R:
                return new ZeroR();

            default:
                LoggerSingleton.getInstance().getLogger().info("Invalid classifier chosen");
                return null;

        }
    }
}
