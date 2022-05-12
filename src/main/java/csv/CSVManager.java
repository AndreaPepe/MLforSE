package csv;

import com.opencsv.CSVWriter;
import exceptions.CSVException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class CSVManager {

    private CSVManager() {
    }

    public static void csvWriteAll(String filename, List<String[]> lines) throws IOException {
        /*
            Write versions to a csv file using OpenCSV
            If the file already exists, delete it; create the file and write it.
            */
        File file = new File(filename);
        Files.deleteIfExists(file.toPath());
        FileWriter writer = new FileWriter(file);
        // we need to put ';' as delimiter
        CSVWriter csvWriter = new CSVWriter(writer, ';', '"', '\\', "\n");
        csvWriter.writeAll(lines);
        csvWriter.close();
        writer.close();
    }
}
