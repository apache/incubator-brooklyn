package brooklyn.entity.basic.lifecycle;

import java.util.List;

public interface ScriptRunner {

    /** runs a script, returns the result code */
    int execute(List<String> script, String summaryForLogging);
	
}
