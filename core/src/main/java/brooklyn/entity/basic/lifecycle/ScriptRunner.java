package brooklyn.entity.basic.lifecycle;

import java.util.List;
import java.util.Map;

public interface ScriptRunner {

    /** runs a script, returns the result code */
    int execute(List<String> script, String summaryForLogging);

    /** runs a script, returns the result code */
//    int execute(Map<?,?> flags, List<String> script, String summaryForLogging);
	
}
