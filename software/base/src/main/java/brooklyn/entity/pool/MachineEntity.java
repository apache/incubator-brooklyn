package brooklyn.entity.pool;

import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.util.time.Duration;

@ImplementedBy(MachineEntityImpl.class)
public interface MachineEntity extends SoftwareProcess {

    AttributeSensor<Duration> UPTIME = MachineAttributes.UPTIME;
    AttributeSensor<Double> LOAD_AVERAGE = MachineAttributes.LOAD_AVERAGE;
    AttributeSensor<Double> CPU_USAGE = MachineAttributes.CPU_USAGE;
    AttributeSensor<Long> FREE_MEMORY = MachineAttributes.FREE_MEMORY;
    AttributeSensor<Long> TOTAL_MEMORY = MachineAttributes.TOTAL_MEMORY;
    AttributeSensor<Long> USED_MEMORY = MachineAttributes.USED_MEMORY;

    MethodEffector<String> EXEC_COMMAND = new MethodEffector<String>(MachineEntity.class, "execCommand");
    MethodEffector<String> EXEC_COMMAND_TIMEOUT = new MethodEffector<String>(MachineEntity.class, "execCommandTimeout");

    /**
     * Execute a command and return the output.
     */
    @Effector(description="Execute a command and return the output")
    String execCommand(
            @EffectorParam(name="command", description="Command") String command);

    /**
     * Execute a command and return the output, or throw an exception after a timeout.
     */
    @Effector(description="Execute a command and return the output")
    String execCommandTimeout(
            @EffectorParam(name="command", description="Command") String command,
            @EffectorParam(name="timeout", description="Timeout") Duration timeout);

}
