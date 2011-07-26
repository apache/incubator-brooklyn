package brooklyn.event.adapter

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.location.basic.SshMachineLocation

import com.google.common.base.Preconditions
import com.google.common.io.CharStreams

/**
 * This class adapts the result of commands sent over SSH to {@link Sensor} data for a particular {@link Entity}, updating the
 * {@link Activity} as required.
 */
public class SshSensorAdapter {
    static final Logger log = LoggerFactory.getLogger(SshSensorAdapter.class);

    final EntityLocal entity
    final SshMachineLocation machine

    public SshSensorAdapter(EntityLocal entity, SshMachineLocation machine) {
        this.entity = entity
        this.machine = machine
    }

    public ValueProvider<Integer> newReturnValueProvider(String command) {
        return new SshReturnValueProvider(command, this)
    }

    public ValueProvider<String> newOutputValueProvider(String command) {
        return new SshOutputValueProvider(command, this)
    }

    public ValueProvider<Boolean> newMatchValueProvider(String command, String regexp) {
        return new SshMatchValueProvider(command, regexp, this)
    }

    /**
     * Returns the exit status of a command.
     */
    private Integer getExitStatus(String command) {
        return machine.run(command)
    }

    /**
     * Returns the output of a command.
     */
    private String getCommandOutput(String command) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        int status = machine.run(out:out, command)
        if (status == 0) {
            log.error "*** {} ***", out
            return out.toString()
        } else return null
    }

    /**
     * Returns true if output of a command matches a regexp.
     */
    private Boolean getCommandOutputMatch(String command, String regexp) {
        String out = getCommandOutput(command)
        if (out == null) return false
        return out =~ regexp
    }
}

/**
 * Provides return value of a command to a sensor.
 */
public class SshReturnValueProvider<Integer> implements ValueProvider<Integer> {
    private final String command
    private final SshSensorAdapter adapter

    public SshReturnValueProvider(String command, SshSensorAdapter adapter) {
        this.command = Preconditions.checkNotNull(command, "command")
        this.adapter = Preconditions.checkNotNull(adapter, "adapter")
    }

    public Boolean compute() {
        return adapter.executeCommand(command)
    }
}

/**
 * Provides output of a command to a sensor.
 */
public class SshOutputValueProvider<String> implements ValueProvider<String> {
    private final String command
    private final SshSensorAdapter adapter

    public SshOutputValueProvider(String command, SshSensorAdapter adapter) {
        this.command = Preconditions.checkNotNull(command, "command")
        this.adapter = Preconditions.checkNotNull(adapter, "adapter")
    }

    public Boolean compute() {
        return adapter.getCommandOutput(command)
    }
}

/**
 * Provides return value of a command to a sensor.
 */
public class SshMatchValueProvider<Boolean> implements ValueProvider<Boolean> {
    private final String command
    private final String regexp
    private final SshSensorAdapter adapter

    public SshMatchValueProvider(String command, String regexp, SshSensorAdapter adapter) {
        this.command = Preconditions.checkNotNull(command, "command")
        this.regexp = Preconditions.checkNotNull(regexp, "regexp")
        this.adapter = Preconditions.checkNotNull(adapter, "adapter")
    }

    public Boolean compute() {
        return adapter.getCommandOutputMatch(command, regexp)
    }
}