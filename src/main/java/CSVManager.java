import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

public class CSVManager {

    public static void csvWriteAll(String filename, List<String[]> lines) throws Exception {
        /*
            Write versions to a csv file using OpenCSV
            If the file already exists, delete it; create the file and write it.
            */
        File file = new File(filename);
        if (file.exists()){
            if(!file.delete())
                throw new Exception("Unable to delete file");
        }
        FileWriter writer = new FileWriter(file);
        // we need to put ';' as delimiter
        CSVWriter csvWriter = new CSVWriter(writer, ';', '"', '\\', "\n");
        csvWriter.writeAll(lines);
        csvWriter.close();
    }
}
