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

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

public interface SoftwareProcess extends Entity, Startable {

    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    public static final AttributeSensor<String> ADDRESS = Attributes.ADDRESS;
    public static final AttributeSensor<String> SUBNET_HOSTNAME = Attributes.SUBNET_HOSTNAME;
    public static final AttributeSensor<String> SUBNET_ADDRESS = Attributes.SUBNET_ADDRESS;

    @SetFromFlag("startTimeout")
    public static final ConfigKey<Duration> START_TIMEOUT = BrooklynConfigKeys.START_TIMEOUT;

    @SetFromFlag("startLatch")
    public static final ConfigKey<Boolean> START_LATCH = BrooklynConfigKeys.START_LATCH;

    @SetFromFlag("installLatch")
    public static final ConfigKey<Boolean> INSTALL_LATCH = BrooklynConfigKeys.INSTALL_LATCH;

    @SetFromFlag("customizeLatch")
    public static final ConfigKey<Boolean> CUSTOMIZE_LATCH = BrooklynConfigKeys.CUSTOMIZE_LATCH;
    
    @SetFromFlag("preLaunchCommand")
    public static final ConfigKey<String> PRE_LAUNCH_COMMAND = BrooklynConfigKeys.PRE_LAUNCH_COMMAND;
    
    @SetFromFlag("postLaunchCommand")
    public static final ConfigKey<String> POST_LAUNCH_COMMAND = BrooklynConfigKeys.POST_LAUNCH_COMMAND;

    @SetFromFlag("launchLatch")
    public static final ConfigKey<Boolean> LAUNCH_LATCH = BrooklynConfigKeys.LAUNCH_LATCH;

    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION = BrooklynConfigKeys.SUGGESTED_VERSION;

    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = Attributes.DOWNLOAD_URL;

    @SetFromFlag("downloadAddonUrls")
    BasicAttributeSensorAndConfigKey<Map<String,String>> DOWNLOAD_ADDON_URLS = Attributes.DOWNLOAD_ADDON_URLS;

    @SetFromFlag("installLabel")
    public static final ConfigKey<String> INSTALL_UNIQUE_LABEL = BrooklynConfigKeys.INSTALL_UNIQUE_LABEL;
    
    @SetFromFlag("expandedInstallDir")
    BasicAttributeSensorAndConfigKey<String> EXPANDED_INSTALL_DIR = BrooklynConfigKeys.EXPANDED_INSTALL_DIR;

    @SetFromFlag("installDir")
    BasicAttributeSensorAndConfigKey<String> INSTALL_DIR = BrooklynConfigKeys.INSTALL_DIR;
    @Deprecated
    public static final ConfigKey<String> SUGGESTED_INSTALL_DIR = BrooklynConfigKeys.SUGGESTED_INSTALL_DIR;

    @SetFromFlag("runDir")
    BasicAttributeSensorAndConfigKey<String> RUN_DIR = BrooklynConfigKeys.RUN_DIR;
    @Deprecated
    public static final ConfigKey<String> SUGGESTED_RUN_DIR = BrooklynConfigKeys.SUGGESTED_RUN_DIR;

    @SetFromFlag("env")
    public static final MapConfigKey<Object> SHELL_ENVIRONMENT = new MapConfigKey<Object>(Object.class,
            "shell.env", "Map of environment variables to pass to the runtime shell", MutableMap.<String,Object>of());

    @SetFromFlag("provisioningProperties")
    public static final MapConfigKey<Object> PROVISIONING_PROPERTIES = new MapConfigKey<Object>(Object.class,
            "provisioning.properties", "Custom properties to be passed in when provisioning a new machine", MutableMap.<String,Object>of());

    @SetFromFlag("maxRebindSensorsDelay")
    ConfigKey<Duration> MAXIMUM_REBIND_SENSOR_CONNECT_DELAY = ConfigKeys.newConfigKey(Duration.class,
            "softwareProcess.maxSensorRebindDelay",
            "The maximum delay to apply when reconnecting sensors when rebinding to this entity. " +
                    "Brooklyn will wait a random amount of time, up to the value of this config key, to " +
                    "avoid a thundering herd problem when the entity shares its machine with " +
                    "several others. Set to null or to 0 to disable any delay.",
            Duration.TEN_SECONDS);

    /** controls the behavior when starting (stop, restart) {@link Startable} children as part of the start (stop, restart) effector on this entity
     * <p>
     * (NB: restarts are currently not propagated to children in the default {@link SoftwareProcess}
     * due to the various semantics which may be desired; this may change, but if entities have specific requirements for restart,
     * developers should either subclass the {@link SoftwareProcessDriverLifecycleEffectorTasks} and/or lean on sensors from the parent */
    public enum ChildStartableMode {
        /** do nothing with {@link Startable} children */
        NONE(true, false, false),
        /** start (stop) {@link Startable} children concurrent with *driver* start (stop),
         * in foreground, so invoking entity will wait for children to complete.
         * <p>
         * if the child requires the parent to reach a particular state before acting,
         * when running in foreground the parent should communicate its state using sensors
         * which the child listens for.
         * note that often sensors at the parent are not activated until it is started,
         * so the usual sensors connected at an entity may not be available when running in this mode */
        FOREGROUND(false, false, false),
        /** as {@link #FOREGROUND} but {@link ChildStartableMode#isLate} */
        FOREGROUND_LATE(false, false, true),
        /** start {@link Startable} children concurrent with *driver* start (stop, restart),
         * but in background, ie disassociated from the effector task at this entity
         * (so that this entity can complete start/stop independent of children) */
        BACKGROUND(false, true, false),
        /** as {@link #BACKGROUND} but {@link ChildStartableMode#isLate} */
        BACKGROUND_LATE(false, true, true);

        /** whether starting (stopping, restarting) children is disabled */
        public final boolean isDisabled;
        /** whether starting (stopping, restarting) children is backgrounded, so parent should not wait on them */
        public final boolean isBackground;
        /** whether starting (stopping, restarting) children should be nested, so start occurs after the driver is started,
         * and stop before the driver is stopped (if false the children operations are concurrent with the parent),
         * (with restart always being done in parallel though this behaviour may change) */
        public final boolean isLate;

        private ChildStartableMode(boolean isDisabled, boolean isBackground, boolean isLate) {
            this.isDisabled = isDisabled;
            this.isBackground = isBackground;
            this.isLate = isLate;
        }

    }

    @SetFromFlag("childStartMode")
    public static final ConfigKey<ChildStartableMode> CHILDREN_STARTABLE_MODE = ConfigKeys.newConfigKey(ChildStartableMode.class, "children.startable.mode");

    @SuppressWarnings("rawtypes")
    public static final AttributeSensor<MachineProvisioningLocation> PROVISIONING_LOCATION = new BasicAttributeSensor<MachineProvisioningLocation>(
            MachineProvisioningLocation.class, "softwareservice.provisioningLocation", "Location used to provision a machine where this is running");

    public static final AttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;
 
    public static final AttributeSensor<String> PID_FILE = Sensors.newStringSensor("softwareprocess.pid.file", "PID file");

}
