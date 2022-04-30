package logging;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

class CustomRecordFormatter extends Formatter {
    @Override
    public String format(final LogRecord r) {
        StringBuilder sb = new StringBuilder();
        sb.append(formatMessage(r)).append(System.getProperty("line.separator"));
        if (null != r.getThrown()) {
            sb.append("Throwable occurred: "); //$NON-NLS-1$
            Throwable t = r.getThrown();
            try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)){
                t.printStackTrace(pw);
                sb.append(sw);
            } catch (IOException e) {
                //ignore
            }
        }
        return sb.toString();
    }
}