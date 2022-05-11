package weka;

import model.DatasetInstance;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.List;

public class ArffGenerator {

    public static void generateArffFromDataset(String[] attributes, List<DatasetInstance> dataset, String relationName, String outputFilename)
            throws IOException {
        File file = new File(outputFilename);
        Files.deleteIfExists(file.toPath());
        FileOutputStream fos = new FileOutputStream(file);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos));
        String line;

        // writing header of arff file
        line = String.format("@relation %s\n", relationName);
        writer.write(line);
        // all attributes are numeric, except the last one which is binary
        for (int i = 0; i < attributes.length - 1; i++) {
            line = String.format("@attribute %s numeric\n", attributes[i]);
            writer.write(line);
        }
        line = String.format("@attribute %s {no,yes}\n", attributes[attributes.length - 1]);
        writer.write(line);

        // writing data
        line = "@data\n";
        writer.write(line);

        for (DatasetInstance instance : dataset){
            line = getStringLineFromDatasetInstance(instance);
            writer.write(line);
        }

        writer.close();
        fos.close();
    }


    /**
     * This method transforms the DatasetInstance class in a string
     * representing a data row of an ARFF file.
     * Fields are:
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
    private static String getStringLineFromDatasetInstance(DatasetInstance instance){

        // float numbers are rounded to the second decimal digit
        StringBuilder builder = new StringBuilder();
        builder.append(instance.getSize()).append(",");
        builder.append(instance.getLocTouched()).append(",");
        builder.append(instance.getLocAdded()).append(",");
        builder.append(instance.getMaxLocAdded()).append(",");
        builder.append(instance.getAvgLocAdded()).append(",");
        builder.append(instance.getNumberOfRevisions()).append(",");
        builder.append(instance.getNumberOfAuthors()).append(",");
        builder.append(instance.getChurn()).append(",");
        builder.append(instance.getMaxChurn()).append(",");
        builder.append(instance.getAvgChurn()).append(",");
        builder.append(instance.getNumberOfFixedBugs()).append(",");
        builder.append(instance.getAge()).append(",");
        builder.append(instance.getWeightedAge()).append(",");
        // the last attribute must be followed by a newline
        builder.append(instance.isBuggyYesOrNot()).append("\n");

        return builder.toString();
    }
}
