package brooklyn.entity.pool;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.time.Duration;

@ImplementedBy(MachineEntityImpl.class)
public interface MachineEntity extends SoftwareProcess {

    AttributeSensor<Duration> UPTIME = Sensors.newSensor(Duration.class, "pool.machine.uptime", "Current uptime");
    AttributeSensor<Double> LOAD_AVERAGE = Sensors.newDoubleSensor("pool.machine.loadAverage", "Current load average");
    AttributeSensor<Double> CPU_USAGE = Sensors.newDoubleSensor("pool.machine.cpu", "Current CPU usage");
    AttributeSensor<Long> FREE_MEMORY = Sensors.newLongSensor("pool.machine.memory.free", "Current free memory");
    AttributeSensor<Long> TOTAL_MEMORY = Sensors.newLongSensor("pool.machine.memory.total", "Total memory");
    AttributeSensor<Long> USED_MEMORY = Sensors.newLongSensor("pool.machine.memory.used", "Current memory usage");

}
