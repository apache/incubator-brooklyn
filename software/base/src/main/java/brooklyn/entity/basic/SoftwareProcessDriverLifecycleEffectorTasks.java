/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.basic;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcess.ChildStartableMode;
import brooklyn.entity.software.MachineLifecycleEffectorTasks;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.management.TaskAdaptable;
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
    public void restart() {
        // children are ignored during restart currently - see ChildStartableMode
        
        if (entity().getDriver() == null) {
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
        entity().getDriver().restart();
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
        
        entity().preStart();
    }

    /** returns how children startables should be handled (reporting none for efficiency if there are no children) */
    protected ChildStartableMode getChildrenStartableModeEffective() {
        if (entity().getChildren().isEmpty()) return ChildStartableMode.NONE;
        ChildStartableMode result = entity().getConfig(SoftwareProcess.CHILDREN_STARTABLE_MODE);
        if (result!=null) return result;
        return ChildStartableMode.NONE;
    }

    @Override
    protected String startProcessesAtMachine(final Supplier<MachineLocation> machineS) {
        ChildStartableMode mode = getChildrenStartableModeEffective();
        TaskAdaptable<?> children = null;
        if (!mode.isDisabled) {
            children = StartableMethods.startingChildren(entity(), machineS.get());
            // submit rather than queue so it runs in parallel
            // (could also wrap as parallel task with driver.start() -
            // but only benefit is that child starts show as child task,
            // rather than bg task, so not worth the code complexity)
            if (!mode.isLate) Entities.submit(entity(), children);
        }
        
        entity().getDriver().start();
        String result = "Started with driver "+entity().getDriver();
        
        if (!mode.isDisabled) {
            if (mode.isLate) {
                DynamicTasks.waitForLast();
                if (mode.isBackground) {
                    Entities.submit(entity(), children);
                } else {
                    // when running foreground late, there is no harm here in queueing
                    DynamicTasks.queue(children);
                }
            }
            if (!mode.isBackground) children.asTask().getUnchecked();
            result += "; children started "+mode;
        }
        return result;
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
        
        entity().preStop();
    }

    @Override
    protected String stopProcessesAtMachine() {
        String result;
        
        ChildStartableMode mode = getChildrenStartableModeEffective();
        TaskAdaptable<?> children = null;
        Exception childException = null;
        
        if (!mode.isDisabled) {
            children = StartableMethods.stoppingChildren(entity());
            
            if (mode.isBackground || !mode.isLate) Entities.submit(entity(), children);
            else {
                DynamicTasks.queue(children);
                try {
                    DynamicTasks.waitForLast();
                } catch (Exception e) {
                    childException = e;
                }
            }
        }
        
        if (entity().getDriver() != null) { 
            entity().getDriver().stop();
            result = "Driver stop completed";
        } else {
            result = "No driver (nothing to do here)";
        }

        if (!mode.isDisabled && !mode.isBackground) {
            try {
                children.asTask().get();
            } catch (Exception e) {
                childException = e;
                log.debug("Error stopping children; continuing and will rethrow if no other errors", e);
            }            
        }
        
        if (childException!=null)
            throw new IllegalStateException(result+"; but error stopping child: "+childException, childException);
        
        return result;
    }

}

