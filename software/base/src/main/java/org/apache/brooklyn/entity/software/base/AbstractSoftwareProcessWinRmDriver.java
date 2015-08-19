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

import com.google.common.collect.ImmutableList;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.ReferenceWithError;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.python.core.PyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public abstract class AbstractSoftwareProcessWinRmDriver extends AbstractSoftwareProcessDriver {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractSoftwareProcessDriver.class);

    AttributeSensor<String> WINDOWS_USERNAME = Sensors.newStringSensor("windows.username",
            "Default Windows username to be used when connecting to the Entity's VM");
    AttributeSensor<String> WINDOWS_PASSWORD = Sensors.newStringSensor("windows.password",
            "Default Windows password to be used when connecting to the Entity's VM");

    public AbstractSoftwareProcessWinRmDriver(EntityLocal entity, WinRmMachineLocation location) {
        super(entity, location);
        entity.setAttribute(WINDOWS_USERNAME, location.config().get(WinRmMachineLocation.USER));
        entity.setAttribute(WINDOWS_PASSWORD, location.config().get(WinRmMachineLocation.PASSWORD));
    }

    @Override
    public void runPreInstallCommand(String command) {
        execute(ImmutableList.of(command));
    }

    @Override
    public void setup() {
        // Default to no-op
    }

    @Override
    public void runPostInstallCommand(String command) {
        execute(ImmutableList.of(command));
    }

    @Override
    public void runPreLaunchCommand(String command) {
        execute(ImmutableList.of(command));
    }

    @Override
    public void runPostLaunchCommand(String command) {
        execute(ImmutableList.of(command));
    }

    @Override
    public WinRmMachineLocation getLocation() {
        return (WinRmMachineLocation)super.getLocation();
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
        return copyTo(new File(source), target);
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

    protected WinRmToolResponse executeCommand(ConfigKey<String> regularCommandKey, ConfigKey<String> powershellCommandKey, boolean allowNoOp) {
        String regularCommand = getEntity().getConfig(regularCommandKey);
        String powershellCommand = getEntity().getConfig(powershellCommandKey);
        if (Strings.isBlank(regularCommand) && Strings.isBlank(powershellCommand)) {
            if (allowNoOp) {
                return new WinRmToolResponse("", "", 0);
            } else {
                throw new IllegalStateException(String.format("Exactly one of %s or %s must be set", regularCommandKey.getName(), powershellCommandKey.getName()));
            }
        } else if (!Strings.isBlank(regularCommand) && !Strings.isBlank(powershellCommand)) {
            throw new IllegalStateException(String.format("%s and %s cannot both be set", regularCommandKey.getName(), powershellCommandKey.getName()));
        }

        ByteArrayOutputStream stdIn = new ByteArrayOutputStream();
        ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
        ByteArrayOutputStream stdErr = new ByteArrayOutputStream();

        Task<?> currentTask = Tasks.current();
        if (currentTask != null) {
            writeToStream(stdIn, Strings.isBlank(regularCommand) ? powershellCommand : regularCommand);
            Tasks.addTagDynamically(BrooklynTaskTags.tagForStreamSoft(BrooklynTaskTags.STREAM_STDIN, stdIn));

            if (BrooklynTaskTags.stream(currentTask, BrooklynTaskTags.STREAM_STDOUT)==null) {
                Tasks.addTagDynamically(BrooklynTaskTags.tagForStreamSoft(BrooklynTaskTags.STREAM_STDOUT, stdOut));
                Tasks.addTagDynamically(BrooklynTaskTags.tagForStreamSoft(BrooklynTaskTags.STREAM_STDERR, stdErr));
            }
        }

        WinRmToolResponse response;
        if (Strings.isBlank(regularCommand)) {
            response = getLocation().executePsScript(ImmutableList.of(powershellCommand));
        } else {
            response = getLocation().executeScript(ImmutableList.of(regularCommand));
        }

        if (currentTask != null) {
            if (BrooklynTaskTags.stream(currentTask, BrooklynTaskTags.STREAM_STDOUT)==null) {
                writeToStream(stdOut, response.getStdOut());
                writeToStream(stdErr, response.getStdErr());
            }
        }

        return response;
    }

    private void writeToStream(ByteArrayOutputStream stream, String string)  {
        try {
            stream.write(string.getBytes());
        } catch (IOException e) {
            LOG.warn("Problem populating one of the std streams for task of entity " + getEntity(), e);
        }
    }

    public int execute(List<String> script) {
        return getLocation().executeScript(script).getStatusCode();
    }

    public int executePsScriptNoRetry(List<String> psScript) {
        return getLocation().executePsScriptNoRetry(psScript).getStatusCode();
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
        } catch (PyException e) {
            // Restarting the computer will cause the command to fail; ignore the exception and continue
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
