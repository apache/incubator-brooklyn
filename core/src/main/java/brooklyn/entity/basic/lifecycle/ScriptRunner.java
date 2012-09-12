package brooklyn.entity.basic.lifecycle;

import java.util.List;
import java.util.Map;

public interface ScriptRunner {

    /** Runs a script and returns the result code */
    int execute(List<String> script, String summaryForLogging);

    /** Runs a script and returns the result code, supporting flags including:
     *  out, err as output/error streams;
     *  logPrefix, prefix string to put in log output;
     *  env, map of environment vars to pass to shell environment */
    int execute(Map flags, List<String> script, String summaryForLogging);

}
