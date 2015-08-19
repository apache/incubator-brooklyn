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
package org.apache.brooklyn.entity.brooklynnode.effector;

import java.util.Map;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.drivers.DriverDependentEntity;
import org.apache.brooklyn.api.mgmt.ha.HighAvailabilityMode;
import org.apache.brooklyn.api.mgmt.ha.ManagementNodeState;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.MapConfigKey;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.EntityTasks;
import org.apache.brooklyn.entity.brooklynnode.BrooklynCluster;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNode;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNodeDriver;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcess.StopSoftwareParameters;
import org.apache.brooklyn.entity.software.base.SoftwareProcess.StopSoftwareParameters.StopMode;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
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
    public static final ConfigKey<Boolean> DO_DRY_RUN_FIRST = ConfigKeys.newBooleanConfigKey(
            "doDryRunFirst", "Test rebinding with a temporary instance before stopping the entity for upgrade.", true);
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
        .parameter(DO_DRY_RUN_FIRST)
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

        final ConfigBag parameters = ConfigBag.newInstanceCopying(parametersO);

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
        
        final String bkName;
        boolean doDryRunFirst = parameters.get(DO_DRY_RUN_FIRST);
        if(doDryRunFirst) {
            bkName = dryRunUpdate(parameters);
        } else {
            bkName = "direct-"+Identifiers.makeRandomId(4);
        }
        
        // Stop running instance
        DynamicTasks.queue(Tasks.builder().name("shutdown node")
                .add(Effectors.invocation(entity(), BrooklynNode.STOP_NODE_BUT_LEAVE_APPS, ImmutableMap.of(StopSoftwareParameters.STOP_MACHINE_MODE, StopMode.NEVER)))
                .build());

        // backup old files
        DynamicTasks.queue(Tasks.builder().name("backup old version").body(new Runnable() {
            @Override
            public void run() {
                String runDir = entity().getAttribute(SoftwareProcess.RUN_DIR);
                String bkDir = Urls.mergePaths(runDir, "..", Urls.getBasename(runDir)+"-backups", bkName);
                log.debug(this+" storing backup of previous version in "+bkDir);
                DynamicTasks.queue(SshEffectorTasks.ssh(
                    "cd "+runDir,
                    "mkdir -p "+bkDir,
                    "mv * "+bkDir
                    // By removing the run dir of the entity we force it to go through
                    // the customize step again on start and re-generate local-brooklyn.properties.
                    ).summary("move files"));
            }
        }).build());
        
        // Reconfigure entity
        DynamicTasks.queue(Tasks.builder().name("reconfigure").body(new Runnable() {
            @Override
            public void run() {
                DynamicTasks.waitForLast();
                ((EntityInternal)entity()).setAttribute(SoftwareProcess.INSTALL_DIR, (String)null);
                entity().setConfig(SoftwareProcess.INSTALL_UNIQUE_LABEL, (String)null);
                entity().getConfigMap().addToLocalBag(parameters.getAllConfig());
                entity().setAttribute(BrooklynNode.DOWNLOAD_URL, entity().getConfig(DOWNLOAD_URL));

                // Setting SUGGESTED_VERSION will result in an new empty INSTALL_FOLDER, but clear it
                // just in case the user specified already installed version.
                ((BrooklynNodeDriver)((DriverDependentEntity<?>)entity()).getDriver()).clearInstallDir();
            }
        }).build());
        
        // Start this entity, running the new version.
        // This will download and install the new dist (if not already done by the dry run node).
        DynamicTasks.queue(Effectors.invocation(entity(), BrooklynNode.START, ConfigBag.EMPTY));

        return null;
    }

    private String dryRunUpdate(ConfigBag parameters) {
        // TODO require entity() node state master or hot standby AND require persistence enabled, or a new 'force_attempt_upgrade' parameter to be applied
        // TODO could have a 'skip_dry_run_upgrade' parameter
        // TODO could support 'dry_run_only' parameter, with optional resumption tasks (eg new dynamic effector)

        // 1 add new brooklyn version entity as child (so uses same machine), with same config apart from things in parameters
        final Entity dryRunChild = entity().addChild(createDryRunSpec()
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
        DynamicTasks.queue(Tasks.builder().name("shutdown transient node")
            .add(Effectors.invocation(dryRunChild, BrooklynNode.STOP_NODE_BUT_LEAVE_APPS, ImmutableMap.of(StopSoftwareParameters.STOP_MACHINE_MODE, StopMode.NEVER)))
            .build());

        DynamicTasks.queue(Tasks.<Void>builder().name("remove transient node").body(
            new Runnable() {
                @Override
                public void run() {
                    Entities.unmanage(dryRunChild);
                }
            }
        ).build());

        return dryRunChild.getId();
    }

    protected EntitySpec<? extends BrooklynNode> createDryRunSpec() {
        return EntitySpec.create(BrooklynNode.class);
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
