package brooklyn.entity.basic;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.software.MachineStartEffectorTask;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.util.task.DynamicTasks;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

public class SoftwareProcessDriverStartEffectorTask extends MachineStartEffectorTask {
    
    private static final Logger log = LoggerFactory.getLogger(SoftwareProcessDriverStartEffectorTask.class);
    
    @Override
    protected SoftwareProcessImpl entity() {
        return (SoftwareProcessImpl)super.entity();
    }

    @Override
    protected Map<String, Object> obtainProvisioningFlags(final MachineProvisioningLocation<?> location) {
        return entity().obtainProvisioningFlags(location);
    }

    @Override
    protected void startInMachineLocationAsync(final Supplier<MachineLocation> machineS) {
        new DynamicTasks.AutoQueueVoid("pre-start") { protected void main() { 
            MachineLocation machine = machineS.get();
            log.info("Starting {} on machine {}", this, machine);
            entity().addLocations(ImmutableList.of((Location)machine));

            entity().initDriver(machine);

            // Note: must only apply config-sensors after adding to locations and creating driver; 
            // otherwise can't do things like acquire free port from location, or allowing driver to set up ports
            ConfigToAttributes.apply(entity());

            if (entity().getAttribute(Attributes.HOSTNAME)==null)
                entity().setAttribute(Attributes.HOSTNAME, machine.getAddress().getHostName());
            if (entity().getAttribute(Attributes.ADDRESS)==null)
                entity().setAttribute(Attributes.ADDRESS, machine.getAddress().getHostAddress());

            // Opportunity to block startup until other dependent components are available
            Object val = entity().getConfig(SoftwareProcess.START_LATCH);
            if (val != null) log.debug("{} finished waiting for start-latch; continuing...", this, val);
            ((SoftwareProcessImpl)entity()).preStart(); 
        }};
        new DynamicTasks.AutoQueueVoid("start (driver)") { protected void main() { 
            ((SoftwareProcessImpl)entity()).getDriver().start();
        }};
        new DynamicTasks.AutoQueueVoid("post-start") { protected void main() {
            ((SoftwareProcessImpl)entity()).postDriverStart();
            ((SoftwareProcessImpl)entity()).connectSensors();
            ((SoftwareProcessImpl)entity()).waitForServiceUp();
            ((SoftwareProcessImpl)entity()).postStart();
        }};
    }

}

