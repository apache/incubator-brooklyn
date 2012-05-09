package brooklyn.event.adapter;
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.io.LineProcessor;

import brooklyn.entity.basic.EntityLocal
import brooklyn.util.ShellUtils


/**
 * Like FunctionSensorAdapter but executes a shell command (on the local machine where this instance of brooklyn is running).
 * Useful e.g. for paas tools such as Cloud Foundry vmc which operate against a remote target.
 * <p>
 * Example usage:
 * <code>
 *   def diskUsage = sensorRegistry.register(new ShellSensorAdapter("df -p"))
 *   diskUsage.then(&parse).with {
 *      poll(DISK0_USAGE_BYTES) { it[0].usage }
 *      poll(DISK0_FREE_BYTES) { it[0].free }
 *   }
 * </code>
 * <p>
 * See also FunctionSensorAdapter (for arbitrary functions) and SshSensorAdapter (to run on remote machines).
 */
public class ShellSensorAdapter extends FunctionSensorAdapter {
    
    public static final Logger log = LoggerFactory.getLogger(ShellSensorAdapter.class);
            
    protected final String command;
    
    public ShellSensorAdapter(Map flags=[:], String command) {
        super(flags, null)
        this.command = command;
    }

    public ShellSensorAdapter process(LineProcessor p) {
        then() { List<String> lines ->
            lines.each { String line -> p.processLine(line) }
            p.result
        }
    }

    // we override call rather than pass a callable in to parent, for efficiency
    public Object call() {
        exec(command);
    }

    public String[] exec(String command) {
        ShellUtils.exec(command, log, entity);
    }        
}
