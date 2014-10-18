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
package brooklyn.entity.brooklynnode;

import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.ReferenceWithError;
import brooklyn.util.guava.Functionals;
import brooklyn.util.net.Urls;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Duration;

import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.reflect.TypeToken;

@SuppressWarnings("serial")
/** Upgrades a brooklyn node in-place on the box, by creating a child brooklyn node and ensuring it can rebind in HOT_STANDBY;
 * requires the target node to have persistence enabled 
 */
public class BrooklynUpgradeEffector {

    private static final Logger log = LoggerFactory.getLogger(BrooklynUpgradeEffector.class);
    
    public static final ConfigKey<String> DOWNLOAD_URL = BrooklynNode.DOWNLOAD_URL.getConfigKey();
    public static final ConfigKey<Map<String,Object>> EXTRA_CONFIG = MapConfigKey.builder(new TypeToken<Map<String,Object>>() {}).name("extraConfig").description("Additional new config to set on this entity as part of upgrading").build();

    public static final Effector<Void> UPGRADE = Effectors.effector(Void.class, "upgrade")
        .description("Changes the Brooklyn build used to run this node, by spawning a dry-run node then copying the installed files across")
        .parameter(BrooklynNode.SUGGESTED_VERSION).parameter(DOWNLOAD_URL).parameter(EXTRA_CONFIG)
        .impl(new UpgradeImpl()).build();
    
    public static class UpgradeImpl extends EffectorBody<Void> {
        @Override
        public Void call(ConfigBag parametersO) {
            ConfigBag parameters = ConfigBag.newInstanceCopying(parametersO);
            
            /*
             * all parameters are passed to children, apart from EXTRA_CONFIG
             * whose value (as a map) is so passed; it provides an easy way to set extra config in the gui.
             * (IOW a key-value mapping can be passed either inside EXTRA_CONFIG or as a sibling to EXTRA_CONFIG)  
             */
            if (parameters.containsKey(EXTRA_CONFIG)) {
                Map<String, Object> extra = parameters.get(EXTRA_CONFIG);
                parameters.remove(EXTRA_CONFIG);
                parameters.putAll(extra);
            }
            log.debug(this+" upgrading, using "+parameters);
            
            // TODO require entity() node state master or hot standby AND require persistence enabled, or a new 'force_attempt_upgrade' parameter to be applied
            // TODO could have a 'skip_dry_run_upgrade' parameter
            // TODO could support 'dry_run_only' parameter, with optional resumption tasks (eg new dynamic effector)

            // 1 add new brooklyn version entity as child (so uses same machine), with same config apart from things in parameters
            final BrooklynNode dryRunChild = entity().addChild(EntitySpec.create(BrooklynNode.class).configure(parameters.getAllConfig())
                .displayName("Upgraded Version Dry-Run Node")
                // force dir and label back to their defaults (do not piggy back on what may have already been installed)
                .configure(BrooklynNode.INSTALL_DIR, BrooklynNode.INSTALL_DIR.getConfigKey().getDefaultValue())
                .configure(BrooklynNode.INSTALL_UNIQUE_LABEL, "upgrade-tmp-"+Identifiers.makeRandomId(8))
                .configure(parameters.getAllConfig()));
            
            //force this to start as hot-standby
            String launchCommand = dryRunChild.getConfig(BrooklynNode.LAUNCH_COMMAND);
            ((EntityInternal)dryRunChild).setConfig(BrooklynNode.LAUNCH_COMMAND, launchCommand + " --highAvailability "+HighAvailabilityMode.HOT_STANDBY);
            
            Entities.manage(dryRunChild);
            final String dryRunNodeUid = dryRunChild.getId();
            ((EntityInternal)dryRunChild).setDisplayName("Dry-Run Upgraded Brooklyn Node ("+dryRunNodeUid+")");

            DynamicTasks.queue(Effectors.invocation(dryRunChild, BrooklynNode.START, ConfigBag.EMPTY));
            
            // 2 confirm hot standby status
            DynamicTasks.queue(newWaitForAttributeTask(dryRunChild, BrooklynNode.MANAGEMENT_NODE_STATE, 
                Predicates.equalTo(ManagementNodeState.HOT_STANDBY), Duration.TWO_MINUTES));

            // 3 stop new version
            // 4 stop old version
            DynamicTasks.queue(Tasks.builder().name("shutdown original and transient nodes")
                .add(Effectors.invocation(dryRunChild, BrooklynNode.SHUTDOWN, ConfigBag.EMPTY))
                .add(Effectors.invocation(entity(), BrooklynNode.SHUTDOWN, ConfigBag.EMPTY))
                .build());
            
            // 5 move old files, and move new files
            DynamicTasks.queue(Tasks.builder().name("setup new version").body(new Runnable() {
                @Override
                public void run() {
                    String runDir = entity().getAttribute(SoftwareProcess.RUN_DIR);
                    String bkDir = Urls.mergePaths(runDir, "..", Urls.getBasename(runDir)+"-backups", dryRunNodeUid);
                    String dryRunDir = Preconditions.checkNotNull(dryRunChild.getAttribute(SoftwareProcess.RUN_DIR));
                    log.debug(this+" storing backup of previous version in "+bkDir);
                    DynamicTasks.queue(SshEffectorTasks.ssh(
                        "cd "+runDir,
                        "mkdir -p "+bkDir,
                        "mv * "+bkDir,
                        "cd "+dryRunDir,
                        "mv * "+runDir
                        ).summary("move files"));
                }
            }).build());

            entity().getConfigMap().addToLocalBag(parameters.getAllConfig());
            
            // 6 start this entity, running the new version
            DynamicTasks.queue(Effectors.invocation(entity(), BrooklynNode.START, ConfigBag.EMPTY));
            
            DynamicTasks.waitForLast();
            Entities.unmanage(dryRunChild);
            
            return null;
        }

    }

    private static class WaitForRepeaterCallable implements Callable<Boolean> {
        protected Repeater repeater;
        protected boolean requireTrue;

        public WaitForRepeaterCallable(Repeater repeater, boolean requireTrue) {
            this.repeater = repeater;
            this.requireTrue = requireTrue;
        }

        @Override
        public Boolean call() {
            ReferenceWithError<Boolean> result = repeater.runKeepingError();
            if (Boolean.TRUE.equals(result.getWithoutError()))
                return true;
            if (result.hasError()) 
                throw Exceptions.propagate(result.getError());
            if (requireTrue)
                throw new IllegalStateException("timeout - "+repeater.getDescription());
            return false;
        }
    }

    private static <T> TaskAdaptable<Boolean> newWaitForAttributeTask(Entity node, AttributeSensor<T> sensor, Predicate<T> condition, Duration timeout) {
        return awaiting( Repeater.create("waiting on "+node+" "+sensor.getName()+" "+condition)
                    .backoff(Duration.millis(10), 1.5, Duration.millis(200))
                    .limitTimeTo(timeout==null ? Duration.PRACTICALLY_FOREVER : timeout)
                    .until(Functionals.callable(Functions.forPredicate(EntityPredicates.attributeSatisfies(sensor, condition)), node)),
                    true);
    }

    private static TaskAdaptable<Boolean> awaiting(Repeater repeater, boolean requireTrue) {
        return Tasks.<Boolean>builder().name(repeater.getDescription()).body(new WaitForRepeaterCallable(repeater, requireTrue)).build();
    }

}
