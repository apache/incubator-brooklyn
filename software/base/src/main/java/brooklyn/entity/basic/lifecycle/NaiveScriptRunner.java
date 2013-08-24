package brooklyn.entity.basic.lifecycle;

import java.util.List;
import java.util.Map;

import brooklyn.util.task.ssh.SshTasks;

/** Marks something which can run scripts. Called "Naive" because it hides too much of the complexity,
 * about script execution and other ssh-related tasks (put, etc). The {@link SshTasks} approach seems better.
 * <p> 
 * Not gone so far as deprecating (yet, in 0.6.0) although we might.  Feedback welcome. 
 * @since 0.6.0 */
@SuppressWarnings("deprecation")
public interface NaiveScriptRunner extends ScriptRunner {

    /** Runs a script and returns the result code */
    int execute(List<String> script, String summaryForLogging);

    /** Runs a script and returns the result code, supporting flags including:
     *  out, err as output/error streams;
     *  logPrefix, prefix string to put in log output;
     *  env, map of environment vars to pass to shell environment */
    int execute(Map flags, List<String> script, String summaryForLogging);

}
