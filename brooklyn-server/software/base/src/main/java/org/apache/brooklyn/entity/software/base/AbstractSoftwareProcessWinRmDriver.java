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
package org.apache.brooklyn.entity.software.base;

import static org.apache.brooklyn.util.JavaGroovyEquivalents.elvis;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.software.base.lifecycle.NativeWindowsScriptRunner;
import org.apache.brooklyn.entity.software.base.lifecycle.WinRmExecuteHelper;
import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.apache.brooklyn.util.core.internal.winrm.WinRmTool;
import org.apache.brooklyn.util.core.internal.winrm.WinRmToolResponse;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.ReferenceWithError;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public abstract class AbstractSoftwareProcessWinRmDriver extends AbstractSoftwareProcessDriver implements NativeWindowsScriptRunner {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractSoftwareProcessWinRmDriver.class);

    AttributeSensor<String> WINDOWS_USERNAME = Sensors.newStringSensor("windows.username",
            "Default Windows username to be used when connecting to the Entity's VM");
    AttributeSensor<String> WINDOWS_PASSWORD = Sensors.newStringSensor("windows.password",
            "Default Windows password to be used when connecting to the Entity's VM");

    public AbstractSoftwareProcessWinRmDriver(EntityLocal entity, WinRmMachineLocation location) {
        super(entity, location);
        entity.sensors().set(WINDOWS_USERNAME, location.config().get(WinRmMachineLocation.USER));
        entity.sensors().set(WINDOWS_PASSWORD, location.config().get(WinRmMachineLocation.PASSWORD));
    }

    /** @see #newScript(Map, String) */
    protected WinRmExecuteHelper newScript(String phase) {
        return newScript(Maps.<String, Object>newLinkedHashMap(), phase);
    }

    protected WinRmExecuteHelper newScript(Map<String, ?> flags, String phase) {
        if (!Entities.isManaged(getEntity()))
            throw new IllegalStateException(getEntity() + " is no longer managed; cannot create script to run here (" + phase + ")");

        WinRmExecuteHelper s = new WinRmExecuteHelper(this, phase + " " + elvis(entity, this));
        return s;
    }

    @Override
    public void runPreInstallCommand() {
        if (Strings.isNonBlank(getEntity().getConfig(VanillaWindowsProcess.PRE_INSTALL_COMMAND)) || Strings.isNonBlank(getEntity().getConfig(VanillaWindowsProcess.PRE_INSTALL_POWERSHELL_COMMAND))) {
            executeCommandInTask(
                    getEntity().getConfig(VanillaWindowsProcess.PRE_INSTALL_COMMAND),
                    getEntity().getConfig(VanillaWindowsProcess.PRE_INSTALL_POWERSHELL_COMMAND),
                    "pre-install-command");
        }
        if (entity.getConfig(VanillaWindowsProcess.PRE_INSTALL_REBOOT_REQUIRED)) {
            rebootAndWait();
        }
    }

    @Override
    public void setup() {
        // Default to no-op
    }

    @Override
    public void runPostInstallCommand() {
        if (Strings.isNonBlank(entity.getConfig(BrooklynConfigKeys.POST_INSTALL_COMMAND)) || Strings.isNonBlank(getEntity().getConfig(VanillaWindowsProcess.POST_INSTALL_POWERSHELL_COMMAND))) {
            executeCommandInTask(
                    getEntity().getConfig(VanillaWindowsProcess.POST_INSTALL_COMMAND),
                    getEntity().getConfig(VanillaWindowsProcess.POST_INSTALL_POWERSHELL_COMMAND),
                    "post-install-command");
        }
    }

    @Override
    public void runPreLaunchCommand() {
        if (Strings.isNonBlank(entity.getConfig(BrooklynConfigKeys.PRE_LAUNCH_COMMAND)) || Strings.isNonBlank(entity.getConfig(VanillaWindowsProcess.PRE_LAUNCH_POWERSHELL_COMMAND))) {
            executeCommandInTask(
                    getEntity().getConfig(VanillaWindowsProcess.PRE_LAUNCH_COMMAND),
                    getEntity().getConfig(VanillaWindowsProcess.PRE_LAUNCH_POWERSHELL_COMMAND),
                    "pre-launch-command");
        }
    }

    @Override
    public void runPostLaunchCommand() {
        if (Strings.isNonBlank(entity.getConfig(BrooklynConfigKeys.POST_LAUNCH_COMMAND)) || Strings.isNonBlank(entity.getConfig(VanillaWindowsProcess.POST_LAUNCH_POWERSHELL_COMMAND))) {
            executeCommandInTask(
                    getEntity().getConfig(VanillaWindowsProcess.POST_LAUNCH_COMMAND),
                    getEntity().getConfig(VanillaWindowsProcess.POST_LAUNCH_POWERSHELL_COMMAND),
                    "post-launch-command");
        }
    }

    @Override
    public WinRmMachineLocation getLocation() {
        return (WinRmMachineLocation)super.getLocation();
    }

    public WinRmMachineLocation getMachine() {
        return getLocation();
    }

    protected int executeCommandInTask(String command, String psCommand, String phase) {
        return newScript(phase)
                .setCommand(command)
                .setPsCommand(psCommand)
                .failOnNonZeroResultCode()
                .gatherOutput()
                .execute();
    }

    @Override
    public int executeNativeCommand(Map flags, String command, String phase) {
        return executeNativeOrPsCommand(flags, command, null, phase, true);
    }

    @Override
    public int executePsCommand(Map flags, String command, String phase) {
        return executeNativeOrPsCommand(flags, null, command, phase, true);
    }

    @Override
    public String getRunDir() {
        // TODO: This needs to be tidied, and read from the appropriate flags (if set)
        return "$HOME\\brooklyn-managed-processes\\apps\\" + entity.getApplicationId() + "\\entities\\" + getEntityVersionLabel()+"_"+entity.getId();
    }

    @Override
    public String getInstallDir() {
        // TODO: This needs to be tidied, and read from the appropriate flags (if set)
        return "$HOME\\brooklyn-managed-processes\\installs\\" + entity.getApplicationId() + "\\" + getEntityVersionLabel()+"_"+entity.getId();
    }

    @Override
    public int copyResource(Map<Object, Object> sshFlags, String source, String target, boolean createParentDir) {
        if (createParentDir) {
            createDirectory(getDirectory(target), "Creating resource directory");
        }
        
        InputStream stream = null;
        try {
            Tasks.setBlockingDetails("retrieving resource "+source+" for copying across");
            stream = resource.getResourceFromUrl(source);
            Tasks.setBlockingDetails("copying resource "+source+" to server");
            return copyTo(stream, target);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        } finally {
            Tasks.setBlockingDetails(null);
            if (stream != null) Streams.closeQuietly(stream);
        }
    }

    @Override
    public int copyResource(Map<Object, Object> sshFlags, InputStream source, String target, boolean createParentDir) {
        if (createParentDir) {
            createDirectory(getDirectory(target), "Creating resource directory");
        }
        return copyTo(source, target);
    }

    @Override
    protected void createDirectory(String directoryName, String summaryForLogging) {
        getLocation().executePsScript("New-Item -path \"" + directoryName + "\" -type directory -ErrorAction SilentlyContinue");
    }

    @Override
    public Integer executeNativeOrPsCommand(Map flags, String regularCommand, String powerShellCommand, String phase, Boolean allowNoOp) {
        if (Strings.isBlank(regularCommand) && Strings.isBlank(powerShellCommand)) {
            if (allowNoOp) {
                return new WinRmToolResponse("", "", 0).getStatusCode();
            } else {
                throw new IllegalStateException(String.format("Exactly one of cmd or psCmd must be set for %s of %s", phase, entity));
            }
        } else if (!Strings.isBlank(regularCommand) && !Strings.isBlank(powerShellCommand)) {
            throw new IllegalStateException(String.format("%s and %s cannot both be set for %s of %s", regularCommand, powerShellCommand, phase, entity));
        }

        ByteArrayOutputStream stdIn = new ByteArrayOutputStream();
        ByteArrayOutputStream stdOut = flags.get("out") != null ? (ByteArrayOutputStream)flags.get("out") : new ByteArrayOutputStream();
        ByteArrayOutputStream stdErr = flags.get("err") != null ? (ByteArrayOutputStream)flags.get("err") : new ByteArrayOutputStream();

        Task<?> currentTask = Tasks.current();
        if (currentTask != null) {
            if (BrooklynTaskTags.stream(Tasks.current(), BrooklynTaskTags.STREAM_STDIN)==null) {
                writeToStream(stdIn, Strings.isBlank(regularCommand) ? powerShellCommand : regularCommand);
                Tasks.addTagDynamically(BrooklynTaskTags.tagForStreamSoft(BrooklynTaskTags.STREAM_STDIN, stdIn));
            }

            if (BrooklynTaskTags.stream(currentTask, BrooklynTaskTags.STREAM_STDOUT)==null) {
                Tasks.addTagDynamically(BrooklynTaskTags.tagForStreamSoft(BrooklynTaskTags.STREAM_STDOUT, stdOut));
                flags.put("out", stdOut);
                Tasks.addTagDynamically(BrooklynTaskTags.tagForStreamSoft(BrooklynTaskTags.STREAM_STDERR, stdErr));
                flags.put("err", stdErr);
            }
        }

        WinRmToolResponse response;
        if (Strings.isBlank(regularCommand)) {
            response = getLocation().executePsScript(ImmutableList.of(powerShellCommand));
        } else {
            response = getLocation().executeCommand(ImmutableList.of(regularCommand));
        }

        if (currentTask != null) {
            writeToStream(stdOut, response.getStdOut());
            writeToStream(stdErr, response.getStdErr());
        }

        return response.getStatusCode();
    }

    private void writeToStream(ByteArrayOutputStream stream, String string) {
        try {
            stream.write(string.getBytes());
        } catch (IOException e) {
            LOG.warn("Problem populating one of the std streams for task of entity " + getEntity(), e);
        }
    }

    public int execute(List<String> script) {
        return getLocation().executeCommand(script).getStatusCode();
    }

    public int executePsScriptNoRetry(List<String> psScript) {
        return getLocation().executePsScript(ImmutableMap.of(WinRmTool.PROP_EXEC_TRIES, 1), psScript).getStatusCode();
    }

    public int executePsScript(List<String> psScript) {
        return getLocation().executePsScript(psScript).getStatusCode();
    }

    public int copyTo(File source, String destination) {
        return getLocation().copyTo(source, destination);
    }

    public int copyTo(InputStream source, String destination) {
        return getLocation().copyTo(source, destination);
    }

    public void rebootAndWait() {
        try {
            executePsScriptNoRetry(ImmutableList.of("Restart-Computer -Force"));
        } catch (Exception e) {
            // Restarting the computer will cause the command to fail; ignore the exception and continue
            Exceptions.propagateIfFatal(e);
        }
        waitForWinRmStatus(false, entity.getConfig(VanillaWindowsProcess.REBOOT_BEGUN_TIMEOUT));
        waitForWinRmStatus(true, entity.getConfig(VanillaWindowsProcess.REBOOT_COMPLETED_TIMEOUT)).getWithError();
    }

    private String getDirectory(String fileName) {
        return fileName.substring(0, fileName.lastIndexOf("\\"));
    }

    private ReferenceWithError<Boolean> waitForWinRmStatus(final boolean requiredStatus, Duration timeout) {
        // TODO: Reduce / remove duplication between this and JcloudsLocation.waitForWinRmAvailable
        Callable<Boolean> checker = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    return (execute(ImmutableList.of("hostname")) == 0) == requiredStatus;
                } catch (Exception e) {
                    return !requiredStatus;
                }
            }
        };

        return new Repeater()
                .every(1, TimeUnit.SECONDS)
                .until(checker)
                .limitTimeTo(timeout)
                .runKeepingError();
    }

}
