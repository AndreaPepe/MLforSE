package weka;

import model.DatasetInstance;

import java.io.*;
import java.nio.file.Files;
import java.util.List;

public class ArffGenerator {

    private ArffGenerator(){}

    public static void generateArffFromDataset(String[] attributes, List<DatasetInstance> dataset, String relationName, String outputFilename)
            throws IOException {
        File file = new File(outputFilename);
        Files.deleteIfExists(file.toPath());
        try(FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            BufferedWriter writer = new BufferedWriter(osw)){

            StringBuilder line = new StringBuilder();

            String newlineChar = System.getProperty("line.separator");
            // writing header of arff file
            line.append(String.format("@relation %s", relationName)).append(newlineChar);
            writer.write(line.toString());
            line.setLength(0);
            // all attributes are numeric, except the last one which is binary
            for (int i = 0; i < attributes.length - 1; i++) {
                line.append(String.format("@attribute %s numeric", attributes[i])).append(newlineChar);
                writer.write(line.toString());
                line.setLength(0);
            }
            line.append(String.format("@attribute %s {no,yes}", attributes[attributes.length - 1])).append(newlineChar);
            writer.write(line.toString());
            line.setLength(0);
            // writing data
            line.append("@data").append(newlineChar);
            writer.write(line.toString());
            line.setLength(0);

            for (DatasetInstance instance : dataset){
                line.append(getStringLineFromDatasetInstance(instance));
                writer.write(line.toString());
                line.setLength(0);
            }
        }


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
