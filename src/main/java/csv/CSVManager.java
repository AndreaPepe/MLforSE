package csv;

import com.opencsv.CSVWriter;
import exceptions.CSVException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CSVManager {

    private CSVManager(){}

    public static void csvWriteAll(String filename, List<String[]> lines) throws CSVException, IOException {
        /*
            Write versions to a csv file using OpenCSV
            If the file already exists, delete it; create the file and write it.
            */
        File file = new File(filename);
        if (file.exists() && !file.delete()){
            throw new CSVException("Unable to delete file");
        }
        FileWriter writer = new FileWriter(file);
        // we need to put ';' as delimiter
        CSVWriter csvWriter = new CSVWriter(writer, ';', '"', '\\', "\n");
        csvWriter.writeAll(lines);
        csvWriter.close();
    }
}
