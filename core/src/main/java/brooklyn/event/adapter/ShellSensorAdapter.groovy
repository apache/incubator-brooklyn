package brooklyn.event.adapter;
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.util.ShellUtils


/**
 * Like FunctionSensorAdapter but executes a shell command.
 * <p>
 * Example usage:
 * <code>
 *   shell.script('df -b').then(&parse(it)).with {
 *      poll(DISK_ZERO_USAGE_BYTES, {it[0].usage})
 *   }
 * </code>
 */
public class ShellSensorAdapter extends FunctionSensorAdapter {
    
    public static final Logger log = LoggerFactory.getLogger(ShellSensorAdapter.class);
            
    protected final String command;
    
    public ShellSensorAdapter(Map flags=[:], String command) {
        super(flags, null)
        this.command = command;
    }

    // we override call rather than pass a callable in to parent, for efficiency
    public Object call() {
        exec(command);
    }

    public String[] exec(String command) {
        ShellUtils.exec(command, log, entity);
    }        
}
