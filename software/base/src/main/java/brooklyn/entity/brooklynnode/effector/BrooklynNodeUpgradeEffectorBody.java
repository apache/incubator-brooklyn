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
package brooklyn.entity.brooklynnode.effector;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityTasks;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcess.StopSoftwareParameters;
import brooklyn.entity.brooklynnode.BrooklynCluster;
import brooklyn.entity.brooklynnode.BrooklynNode;
import brooklyn.entity.brooklynnode.BrooklynNodeDriver;
import brooklyn.entity.drivers.DriverDependentEntity;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.net.Urls;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.reflect.TypeToken;

@SuppressWarnings("serial")
/** Upgrades a brooklyn node in-place on the box, 
 * by creating a child brooklyn node and ensuring it can rebind in HOT_STANDBY
 * <p>
 * Requires the target node to have persistence enabled. 
 */
public class BrooklynNodeUpgradeEffectorBody extends EffectorBody<Void> {

    private static final Logger log = LoggerFactory.getLogger(BrooklynNodeUpgradeEffectorBody.class);
    
    public static final ConfigKey<String> DOWNLOAD_URL = BrooklynNode.DOWNLOAD_URL.getConfigKey();
    public static final ConfigKey<Map<String,Object>> EXTRA_CONFIG = MapConfigKey.builder(new TypeToken<Map<String,Object>>() {})
        .name("extraConfig")
        .description("Additional new config to set on the BrooklynNode as part of upgrading")
        .build();

    public static final Effector<Void> UPGRADE = Effectors.effector(Void.class, "upgrade")
        .description("Changes the Brooklyn build used to run this node, "
            + "by spawning a dry-run node then copying the installed files across. "
            + "This node must be running for persistence for in-place upgrading to work.")
        .parameter(BrooklynNode.SUGGESTED_VERSION)
        .parameter(DOWNLOAD_URL)
        .parameter(EXTRA_CONFIG)
        .impl(new BrooklynNodeUpgradeEffectorBody()).build();
    
    @Override
    public Void call(ConfigBag parametersO) {
        if (!isPersistenceModeEnabled(entity())) {
            // would could try a `forcePersistNow`, but that's sloppy; 
            // for now, require HA/persistence for upgrading
            DynamicTasks.queue( Tasks.warning("Check persistence", 
                new IllegalStateException("Persistence does not appear to be enabled at this cluster. "
                + "In-place node upgrade will not succeed unless a custom launch script enables it.")) );
        }

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
        final BrooklynNode dryRunChild = entity().addChild(EntitySpec.create(BrooklynNode.class)
            .displayName("Upgraded Version Dry-Run Node")
            // install dir and label are recomputed because they are not inherited, and download_url will normally be different
            .configure(parameters.getAllConfig()));

        //force this to start as hot-standby
        // TODO alternatively could use REST API as in BrooklynClusterUpgradeEffectorBody
        String launchParameters = dryRunChild.getConfig(BrooklynNode.EXTRA_LAUNCH_PARAMETERS);
        if (Strings.isBlank(launchParameters)) launchParameters = "";
        else launchParameters += " ";
        launchParameters += "--highAvailability "+HighAvailabilityMode.HOT_STANDBY;
        ((EntityInternal)dryRunChild).setConfig(BrooklynNode.EXTRA_LAUNCH_PARAMETERS, launchParameters);

        Entities.manage(dryRunChild);
        final String dryRunNodeUid = dryRunChild.getId();
        ((EntityInternal)dryRunChild).setDisplayName("Dry-Run Upgraded Brooklyn Node ("+dryRunNodeUid+")");

        DynamicTasks.queue(Effectors.invocation(dryRunChild, BrooklynNode.START, ConfigBag.EMPTY));

        // 2 confirm hot standby status
        DynamicTasks.queue(EntityTasks.requiringAttributeEventually(dryRunChild, BrooklynNode.MANAGEMENT_NODE_STATE, 
            Predicates.equalTo(ManagementNodeState.HOT_STANDBY), Duration.FIVE_MINUTES));

        // 3 stop new version
        // 4 stop old version
        ConfigBag stopParameters = ConfigBag.newInstance();
        stopParameters.put(StopSoftwareParameters.STOP_MACHINE, Boolean.FALSE);
        DynamicTasks.queue(Tasks.builder().name("shutdown original and transient nodes")
            .add(Effectors.invocation(dryRunChild, BrooklynNode.STOP_NODE_BUT_LEAVE_APPS, stopParameters))
            .add(Effectors.invocation(entity(), BrooklynNode.STOP_NODE_BUT_LEAVE_APPS, stopParameters))
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
        
        DynamicTasks.waitForLast();
        entity().setConfig(SoftwareProcess.INSTALL_UNIQUE_LABEL, null);
        entity().getConfigMap().addToLocalBag(parameters.getAllConfig());
        ((BrooklynNodeDriver)((DriverDependentEntity<?>)entity()).getDriver()).clearInstallDir();

        // 6 start this entity, running the new version
        DynamicTasks.queue(Effectors.invocation(entity(), BrooklynNode.START, ConfigBag.EMPTY));

        DynamicTasks.waitForLast();
        Entities.unmanage(dryRunChild);

        return null;
    }

    @Beta
    static boolean isPersistenceModeEnabled(Entity entity) {
        // TODO when there are PERSIST* options in BrooklynNode, look at them here!
        // or, even better, make a REST call to check persistence
        String params = null;
        if (entity instanceof BrooklynCluster) {
            EntitySpec<?> spec = entity.getConfig(BrooklynCluster.MEMBER_SPEC);
            params = Strings.toString( spec.getConfig().get(BrooklynNode.EXTRA_LAUNCH_PARAMETERS) );
        }
        if (params==null) params = entity.getConfig(BrooklynNode.EXTRA_LAUNCH_PARAMETERS);
        if (params==null) return false;
        if (params.indexOf("persist")==0) return false;
        return true;
    }

}
