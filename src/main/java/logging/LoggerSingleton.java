package logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.*;

import static java.lang.System.exit;

public class LoggerSingleton {

    private static LoggerSingleton instance = null;
    private static Logger logger = null;

    protected LoggerSingleton(){

    }

    public static synchronized LoggerSingleton getInstance(){
        if (instance == null)
            instance = new LoggerSingleton();
        return instance;
    }

    public synchronized  Logger getLogger(){
        if (logger == null){
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("logging.properties");
            try {
                LogManager.getLogManager().readConfiguration(inputStream);
                logger = Logger.getLogger("Logger");
            } catch (IOException e) {
                // Logger does not work
                exit(-20);
            }
        }
        return logger;
    }
}
