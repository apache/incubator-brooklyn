package brooklyn.entity.basic;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.software.MachineLifecycleEffectorTasks;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.base.Supplier;

/** Thin shim delegating to driver to do start/stop/restart, wrapping as tasks,
 * with common code pulled up to {@link MachineLifecycleEffectorTasks} for non-driver usage 
 * @since 0.6.0 */
@Beta
public class SoftwareProcessDriverLifecycleEffectorTasks extends MachineLifecycleEffectorTasks {
    
    private static final Logger log = LoggerFactory.getLogger(SoftwareProcessDriverLifecycleEffectorTasks.class);
    
    @Override
    protected void restart() {
        if (((SoftwareProcessImpl)entity()).getDriver() == null) { 
            log.debug("restart of "+entity()+" has no driver - doing machine-level restart");
            super.restart();
            return;
        }
        
        if (Strings.isEmpty(entity().getAttribute(Attributes.HOSTNAME))) {
            log.debug("restart of "+entity()+" has no hostname - doing machine-level restart");
            super.restart();
            return;
        }
        
        log.debug("restart of "+entity()+" appears to have driver and hostname - doing driver-level restart");
        ((SoftwareProcessImpl)entity()).getDriver().restart();
        DynamicTasks.queue("post-restart", new Runnable() { public void run() {
            postStartCustom();
            if (entity().getAttribute(Attributes.SERVICE_STATE) == Lifecycle.STARTING) 
                entity().setAttribute(Attributes.SERVICE_STATE, Lifecycle.RUNNING);
        }});
    }
    
    @Override
    protected SoftwareProcessImpl entity() {
        return (SoftwareProcessImpl) super.entity();
    }
    
    @Override
    protected Map<String, Object> obtainProvisioningFlags(final MachineProvisioningLocation<?> location) {
        return entity().obtainProvisioningFlags(location);
    }
     
    @Override
    protected void preStartCustom(MachineLocation machine) {
        entity().initDriver(machine);

        // Note: must only apply config-sensors after adding to locations and creating driver; 
        // otherwise can't do things like acquire free port from location, or allowing driver to set up ports
        super.preStartCustom(machine);
        
        ((SoftwareProcessImpl)entity()).preStart(); 
    }

    @Override
    protected String startProcessesAtMachine(final Supplier<MachineLocation> machineS) {
        entity().getDriver().start();
        return "Started with driver "+entity().getDriver();
    }

    @Override
    protected void postStartCustom() {
        entity().postDriverStart();
        if (entity().connectedSensors) {
            // many impls aren't idempotent - though they should be!
            log.debug("skipping connecting sensors for "+entity()+" in driver-tasks postStartCustom because already connected (e.g. restarting)");
        } else {
            log.debug("connecting sensors for "+entity()+" in driver-tasks postStartCustom because already connected (e.g. restarting)");
            entity().connectSensors();
        }
        entity().waitForServiceUp();
        entity().postStart();
    }
    
    @Override
    protected void preStopCustom() {
        super.preStopCustom();
        
        ((SoftwareProcessImpl)entity()).preStop(); 
    }

    @Override
    protected String stopProcessesAtMachine() {
        if (entity().getDriver() != null) { 
            entity().getDriver().stop();
            return "Driver stop completed";
        }
        return "No driver (nothing to do)";
    }

}

