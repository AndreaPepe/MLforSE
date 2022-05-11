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
        InputStream streamTraining = new FileInputStream(trainingSet);
        InputStream streamTesting = new FileInputStream(testingSet);
        ConverterUtils.DataSource sourceTraining = new ConverterUtils.DataSource(streamTraining);
        Instances training = sourceTraining.getDataSet();
        ConverterUtils.DataSource sourceTesting = new ConverterUtils.DataSource(streamTesting);
        Instances testing = sourceTesting.getDataSet();

        int numAttr = training.numAttributes();
        // setting the last attribute as the attribute to estimate
        training.setClassIndex(numAttr - 1);
        testing.setClassIndex(numAttr - 1);

        AbstractClassifier classifier;
        List<ClassifierEvaluation> classifierEvaluations = new ArrayList<>();
        for (ClassifierType classifierName : this.classifiers) {
            classifier = handleClassifier(classifierName);

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

        streamTraining.close();
        streamTesting.close();
        return classifierEvaluations;
    }

    private AbstractClassifier handleClassifier(ClassifierType classifierType) {
        AbstractClassifier ret = null;
        switch (classifierType) {
            case NaiveBayes -> ret = new NaiveBayes();
            case J48 -> ret = new J48();
            case RandomForest -> ret = new RandomForest();
            case IBk -> ret = new IBk();
            case ZeroR -> ret = new ZeroR();
            default -> LoggerSingleton.getInstance().getLogger().info("Invalid classifier chosen");
        }
        return ret;
    }
}
