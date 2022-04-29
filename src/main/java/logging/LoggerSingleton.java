package logging;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

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
            logger = Logger.getLogger(LoggerSingleton.class.getName());
            for(Handler iHandler:logger.getParent().getHandlers())
            {
                logger.getParent().removeHandler(iHandler);
            }

            CustomRecordFormatter formatter = new CustomRecordFormatter();
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(formatter);
            logger.addHandler(consoleHandler);
        }
        return logger;
    }
}
