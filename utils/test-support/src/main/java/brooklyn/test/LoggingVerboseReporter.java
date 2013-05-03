package brooklyn.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingVerboseReporter extends VerboseReporter {

    private static final Logger log = LoggerFactory.getLogger(LoggingVerboseReporter.class);
    
    public LoggingVerboseReporter() { super(); }
    public LoggingVerboseReporter(String prefix) { super(prefix); }

    @Override
    protected void log(String message) {
        log.info("TESTNG "+message);
    }
    
}
