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
package brooklyn.entity.software;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigUtils;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.EffectorTasks;
import brooklyn.entity.effector.EffectorTasks.EffectorTaskFactory;
import brooklyn.location.Location;
import brooklyn.location.basic.LocationInternal;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.Tasks;
import brooklyn.util.task.ssh.SshFetchTaskFactory;
import brooklyn.util.task.ssh.SshFetchTaskWrapper;
import brooklyn.util.task.ssh.SshPutTaskFactory;
import brooklyn.util.task.ssh.SshPutTaskWrapper;
import brooklyn.util.task.ssh.SshTasks;
import brooklyn.util.task.ssh.internal.AbstractSshExecTaskFactory;
import brooklyn.util.task.ssh.internal.PlainSshExecTaskFactory;
import brooklyn.util.task.system.ProcessTaskFactory;
import brooklyn.util.task.system.ProcessTaskWrapper;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.collect.Maps;

/**
 * Conveniences for generating {@link Task} instances to perform SSH activities.
 * <p>
 * If the {@link SshMachineLocation machine} is not specified directly it
 * will be inferred from the {@link Entity} context of either the {@link Effector}
 * or the current {@link Task}.
 * 
 * @see SshTasks
 * @since 0.6.0
 */
@Beta
public class SshEffectorTasks {

    private static final Logger log = LoggerFactory.getLogger(SshEffectorTasks.class);
    
    public static final ConfigKey<Boolean> IGNORE_ENTITY_SSH_FLAGS = ConfigKeys.newBooleanConfigKey("ignoreEntitySshFlags",
        "Whether to ignore any ssh flags (behaviour constraints) set on the entity or location " +
        "where this is running, using only flags explicitly specified", false);
    
    /** like {@link EffectorBody} but providing conveniences when in a {@link SoftwareProcess}
     * (or other entity with a single machine location) */
    public abstract static class SshEffectorBody<T> extends EffectorBody<T> {
        
        /** convenience for accessing the machine */
        public SshMachineLocation machine() {
            return EffectorTasks.getSshMachine(entity());
        }

        /** convenience for generating an {@link PlainSshExecTaskFactory} which can be further customised if desired, and then (it must be explicitly) queued */
        public ProcessTaskFactory<Integer> ssh(String ...commands) {
            return new SshEffectorTaskFactory<Integer>(commands).machine(machine());
        }
    }

    /** variant of {@link PlainSshExecTaskFactory} which fulfills the {@link EffectorTaskFactory} signature so can be used directly as an impl for an effector,
     * also injects the machine automatically; can also be used outwith effector contexts, and machine is still injected if it is
     * run from inside a task at an entity with a single SshMachineLocation */
    public static class SshEffectorTaskFactory<RET> extends AbstractSshExecTaskFactory<SshEffectorTaskFactory<RET>,RET> implements EffectorTaskFactory<RET> {

        public SshEffectorTaskFactory(String ...commands) {
            super(commands);
        }
        public SshEffectorTaskFactory(SshMachineLocation machine, String ...commands) {
            super(machine, commands);
        }
        @Override
        public ProcessTaskWrapper<RET> newTask(Entity entity, Effector<RET> effector, ConfigBag parameters) {
            markDirty();
            if (summary==null) summary(effector.getName()+" (ssh)");
            machine(EffectorTasks.getSshMachine(entity));
            return newTask();
        }
        @Override
        public synchronized ProcessTaskWrapper<RET> newTask() {
            Entity entity = BrooklynTaskTags.getTargetOrContextEntity(Tasks.current());
            if (machine==null) {
                if (log.isDebugEnabled())
                    log.debug("Using an ssh task not in an effector without any machine; will attempt to infer the machine: "+this);
                if (entity!=null)
                    machine(EffectorTasks.getSshMachine(entity));
            }
            applySshFlags(getConfig(), entity, getMachine());
            return super.newTask();
        }
        
        @Override
        public <T2> SshEffectorTaskFactory<T2> returning(ScriptReturnType type) {
            return (SshEffectorTaskFactory<T2>) super.<T2>returning(type);
        }

        @Override
        public SshEffectorTaskFactory<Boolean> returningIsExitCodeZero() {
            return (SshEffectorTaskFactory<Boolean>) super.returningIsExitCodeZero();
        }

        public SshEffectorTaskFactory<String> requiringZeroAndReturningStdout() {
            return (SshEffectorTaskFactory<String>) super.requiringZeroAndReturningStdout();
        }
        
        public <RET2> SshEffectorTaskFactory<RET2> returning(Function<ProcessTaskWrapper<?>, RET2> resultTransformation) {
            return (SshEffectorTaskFactory<RET2>) super.returning(resultTransformation);
        }
    }
    
    public static class SshPutEffectorTaskFactory extends SshPutTaskFactory implements EffectorTaskFactory<Void> {
        public SshPutEffectorTaskFactory(String remoteFile) {
            super(remoteFile);
        }
        public SshPutEffectorTaskFactory(SshMachineLocation machine, String remoteFile) {
            super(machine, remoteFile);
        }
        @Override
        public SshPutTaskWrapper newTask(Entity entity, Effector<Void> effector, ConfigBag parameters) {
            machine(EffectorTasks.getSshMachine(entity));
            applySshFlags(getConfig(), entity, getMachine());
            return super.newTask();
        }
        @Override
        public SshPutTaskWrapper newTask() {
            Entity entity = BrooklynTaskTags.getTargetOrContextEntity(Tasks.current());
            if (machine==null) {
                if (log.isDebugEnabled())
                    log.debug("Using an ssh put task not in an effector without any machine; will attempt to infer the machine: "+this);
                if (entity!=null) {
                    machine(EffectorTasks.getSshMachine(entity));
                }

            }
            applySshFlags(getConfig(), entity, getMachine());
            return super.newTask();
        }
    }

    public static class SshFetchEffectorTaskFactory extends SshFetchTaskFactory implements EffectorTaskFactory<String> {
        public SshFetchEffectorTaskFactory(String remoteFile) {
            super(remoteFile);
        }
        public SshFetchEffectorTaskFactory(SshMachineLocation machine, String remoteFile) {
            super(machine, remoteFile);
        }
        @Override
        public SshFetchTaskWrapper newTask(Entity entity, Effector<String> effector, ConfigBag parameters) {
            machine(EffectorTasks.getSshMachine(entity));
            applySshFlags(getConfig(), entity, getMachine());
            return super.newTask();
        }
        @Override
        public SshFetchTaskWrapper newTask() {
            Entity entity = BrooklynTaskTags.getTargetOrContextEntity(Tasks.current());
            if (machine==null) {
                if (log.isDebugEnabled())
                    log.debug("Using an ssh fetch task not in an effector without any machine; will attempt to infer the machine: "+this);
                if (entity!=null)
                    machine(EffectorTasks.getSshMachine(entity));
            }
            applySshFlags(getConfig(), entity, getMachine());
            return super.newTask();
        }
    }

    public static SshEffectorTaskFactory<Integer> ssh(String ...commands) {
        return new SshEffectorTaskFactory<Integer>(commands);
    }

    public static SshEffectorTaskFactory<Integer> ssh(List<String> commands) {
        return ssh(commands.toArray(new String[commands.size()]));
    }

    public static SshPutTaskFactory put(String remoteFile) {
        return new SshPutEffectorTaskFactory(remoteFile);
    }

    public static SshFetchEffectorTaskFactory fetch(String remoteFile) {
        return new SshFetchEffectorTaskFactory(remoteFile);
    }

    /** task which returns 0 if pid is running */
    public static SshEffectorTaskFactory<Integer> codePidRunning(Integer pid) {
        return ssh("ps -p "+pid).summary("PID "+pid+" is-running check (exit code)").allowingNonZeroExitCode();
    }
    
    /** task which fails if the given PID is not running */
    public static SshEffectorTaskFactory<?> requirePidRunning(Integer pid) {
        return codePidRunning(pid).summary("PID "+pid+" is-running check (required)").requiringExitCodeZero("Process with PID "+pid+" is required to be running");
    }

    /** as {@link #codePidRunning(Integer)} but returning boolean */
    public static SshEffectorTaskFactory<Boolean> isPidRunning(Integer pid) {
        return codePidRunning(pid).summary("PID "+pid+" is-running check (boolean)").returning(new Function<ProcessTaskWrapper<?>, Boolean>() {
            public Boolean apply(@Nullable ProcessTaskWrapper<?> input) { return Integer.valueOf(0).equals(input.getExitCode()); }
        });
    }


    /** task which returns 0 if pid in the given file is running;
     * method accepts wildcards so long as they match a single file on the remote end
     * <p>
     * returns 1 if no matching file, 
     * 1 if matching file but no matching process,
     * and 2 if 2+ matching files */
    public static SshEffectorTaskFactory<Integer> codePidFromFileRunning(final String pidFile) {
        return ssh(BashCommands.chain(
                // this fails, but isn't an error
                BashCommands.requireTest("-f "+pidFile, "The PID file "+pidFile+" does not exist."),
                // this fails and logs an error picked up later
                BashCommands.requireTest("`ls "+pidFile+" | wc -w` -eq 1", "ERROR: there are multiple matching PID files"),
                // this fails and logs an error picked up later
                BashCommands.require("cat "+pidFile, "ERROR: the PID file "+pidFile+" cannot be read (permissions?)."),
                // finally check the process
                "ps -p `cat "+pidFile+"`")).summary("PID file "+pidFile+" is-running check (exit code)")
                .allowingNonZeroExitCode()
                .addCompletionListener(new Function<ProcessTaskWrapper<?>,Void>() {
                    public Void apply(ProcessTaskWrapper<?> input) {
                        if (input.getStderr().contains("ERROR:"))
                            throw new IllegalStateException("Invalid or inaccessible PID filespec: "+pidFile);
                        return null;
                    }
                });
    }
    
    /** task which fails if the pid in the given file is not running (or if there is no such PID file);
     * method accepts wildcards so long as they match a single file on the remote end (fails if 0 or 2+ matching files) */
    public static SshEffectorTaskFactory<?> requirePidFromFileRunning(String pidFile) {
        return codePidFromFileRunning(pidFile)
                .summary("PID file "+pidFile+" is-running check (required)")
                .requiringExitCodeZero("Process with PID from file "+pidFile+" is required to be running");
    }

    /** as {@link #codePidFromFileRunning(String)} but returning boolean */
    public static SshEffectorTaskFactory<Boolean> isPidFromFileRunning(String pidFile) {
        return codePidFromFileRunning(pidFile).summary("PID file "+pidFile+" is-running check (boolean)").
                returning(new Function<ProcessTaskWrapper<?>, Boolean>() {
                    public Boolean apply(@Nullable ProcessTaskWrapper<?> input) { return ((Integer)0).equals(input.getExitCode()); }
                });
    }

    /** extracts the values for the main brooklyn.ssh.config.* config keys (i.e. those declared in ConfigKeys) 
     * as declared on the entity, and inserts them in a map using the unprefixed state, for ssh.
     * <p>
     * currently this is computed for each call, which may be wasteful, but it is reliable in the face of config changes.
     * we could cache the Map.  note that we do _not_ cache (or even own) the SshTool; 
     * the SshTool is created or re-used by the SshMachineLocation making use of these properties */
    @Beta
    public static Map<String, Object> getSshFlags(Entity entity, Location optionalLocation) {
        ConfigBag allConfig = ConfigBag.newInstance();
        
        StringConfigMap globalConfig = ((EntityInternal)entity).getManagementContext().getConfig();
        allConfig.putAll(globalConfig.getAllConfig());
        
        if (optionalLocation!=null)
            allConfig.putAll(((LocationInternal)optionalLocation).getAllConfigBag());
        
        allConfig.putAll(((EntityInternal)entity).getAllConfig());
        
        Map<String, Object> result = Maps.newLinkedHashMap();
        for (String keyS : allConfig.getAllConfig().keySet()) {
            if (keyS.startsWith(SshTool.BROOKLYN_CONFIG_KEY_PREFIX)) {
                ConfigKey<?> key = ConfigKeys.newConfigKey(Object.class, keyS);
                
                Object val = allConfig.getStringKey(keyS);
                
                /*
                 * NOV 2013 changing this to rely on config above being inserted in the right order,
                 * so entity config will be preferred over location, and location over global.
                 * If that is consistent then remove the lines below.
                 * (We can also accept null entity and so combine with SshTasks.getSshFlags.)
                 */
                
//                // have to use raw config to test whether the config is set
//                Object val = ((EntityInternal)entity).getConfigMap().getRawConfig(key);
//                if (val!=null) {
//                    val = entity.getConfig(key);
//                } else {
//                    val = globalConfig.getRawConfig(key);
//                    if (val!=null) val = globalConfig.getConfig(key);
//                }
//                if (val!=null) {
                    result.put(ConfigUtils.unprefixedKey(SshTool.BROOKLYN_CONFIG_KEY_PREFIX, key).getName(), val);
//                }
            }
        }
        return result;
    }

    private static void applySshFlags(ConfigBag config, Entity entity, Location machine) {
        if (entity!=null) {
            if (!config.get(IGNORE_ENTITY_SSH_FLAGS)) {
                config.putIfAbsent(getSshFlags(entity, machine));
            }
        }
    }

}
