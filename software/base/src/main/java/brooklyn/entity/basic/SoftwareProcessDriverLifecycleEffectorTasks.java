package brooklyn.entity.basic;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.software.MachineLifecycleEffectorTasks;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.util.task.DynamicTasks;

import com.google.common.base.Supplier;

/** Thin shim delegating to driver to do start/stop/restart */
public class SoftwareProcessDriverLifecycleEffectorTasks extends MachineLifecycleEffectorTasks {
    
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(SoftwareProcessDriverLifecycleEffectorTasks.class);
    
    @Override
    protected void restart() {
        if (((SoftwareProcessImpl)entity()).getDriver() == null) 
            throw new IllegalStateException("entity "+this+" not set up for operations (restart)");
        ((SoftwareProcessImpl)entity()).getDriver().restart();
        DynamicTasks.queue("post-restart", new Runnable() { public void run() {
            ((SoftwareProcessImpl)entity()).postDriverRestart();
            DynamicTasks.waitForLast();
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
        entity().connectSensors();
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

