package brooklyn.event.adapter

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;

import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.basic.lifecycle.ScriptRunner
import brooklyn.util.ShellUtils

/**
 * Like {@link ShellSensorAdapter} but executes the shell command remotely.
 *
 * Useful for entities that only allow interaction via command line tools, such as Rabbit MQ or Redis.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 *   def status = sensorRegistry.register(new SshShellSensorAdapter(driver, "rabbitmqctl -q status"))
 *   status.poll(SERVICE_UP) {
 *     it =~ /running_applications.*RabbitMQ/
 *   }
 * }
 * </pre>
 * <p>
 * Note that the {@link ScriptRunner} pssed to the adapter need not execute the shell
 * commands over ssh, for example when  running entities locally.
 *
 * @see FunctionSensorAdapter
 * @see SshSensorAdapter
 * 
 * @deprecated See brooklyn.event.feed.ssh.SshFeed
 */
@Deprecated
public class SshShellSensorAdapter extends ShellSensorAdapter {
    
    public static final Logger log = LoggerFactory.getLogger(SshShellSensorAdapter.class)
            
    protected final ScriptRunner driver
    
    public SshShellSensorAdapter(Map flags=[:], ScriptRunner driver, String command) {
        super(flags, command)
        this.driver = Preconditions.checkNotNull(driver, "driver")
    }

    public String[] exec(String command) {
        if (log.isDebugEnabled()) log.debug "Polling for {} sensors using {}", entity, command
        ByteArrayOutputStream stdout = []

        def exitStatus = driver.execute(out:stdout, [ command ], "Polling ssh sensors for ${entity}")
        if (exitStatus != 0) throw new IllegalStateException("Error executing \"${command}\", exited with status ${exitStatus}: ${stdout}")
        return stdout.toString(Charsets.UTF_8.name()).split("\n");
    }        
}
