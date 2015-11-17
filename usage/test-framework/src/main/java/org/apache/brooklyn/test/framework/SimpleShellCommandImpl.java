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
package org.apache.brooklyn.test.framework;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.mgmt.TaskFactory;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static org.apache.brooklyn.core.entity.lifecycle.Lifecycle.*;
import static org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.setExpectedState;
import static org.apache.brooklyn.util.text.Strings.isBlank;
import static org.apache.brooklyn.util.text.Strings.isNonBlank;

/**
 * Implementation for {@link SimpleShellCommand}.
 */
public class SimpleShellCommandImpl extends AbstractEntity implements SimpleShellCommand {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleShellCommandImpl.class);
    private static final int A_LINE = 80;
    public static final String DEFAULT_NAME = "download.sh";
    private static final String CD = "cd";

    @Override
    public void init() {
        super.init();
        getLifecycleEffectorTasks().attachLifecycleEffectors(this);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        addLocations(filterLocations(locations));
        setExpectedState(this, STARTING);
    }


    @Override
    public void stop() {
        LOG.debug("{} Stopping simple command", this);
        setUpAndRunState(false, STOPPED);
    }

    @Override
    public void restart() {
        LOG.debug("{} Restarting simple command", this);
        setUpAndRunState(true, RUNNING);
    }

    protected SimpleShellCommandLifecycleEffectorTasks getLifecycleEffectorTasks() {
        return new SimpleShellCommandLifecycleEffectorTasks();
    }

    /**
     * Gives the opportunity to sub-classes to do additional work based on the result of the command.
     */
    protected void handle(SimpleShellCommand.Result result) {
        LOG.debug("{}, Result is {}\nwith output [\n{}\n] and error [\n{}\n]", new Object[] {
            this, result.getExitCode(), shorten(result.getStdout()), shorten(result.getStderr())
        });
    }

    private String shorten(String text) {
        return Strings.maxlenWithEllipsis(text, A_LINE);
    }

    /**
     * Does nothing in this class but gives sub-classes the opportunity to filter locations according to some criterion.
     */
    public Collection<? extends Location> filterLocations(Collection<? extends Location> locations) {
        return locations;
    }

    private void setUpAndRunState(boolean up, Lifecycle status) {
        sensors().set(SERVICE_UP, up);
        setExpectedState(this, status);
    }

    public void execute(MachineLocation machineLocation) {
        try {
            executeCommand(machineLocation);
            setUpAndRunState(true, RUNNING);
        } catch (Exception e) {
            setUpAndRunState(false, ON_FIRE);
            throw Exceptions.propagate(e);
        }
    }

    private void executeCommand(MachineLocation machineLocation) {

        SimpleShellCommand.Result result = null;
        String downloadUrl = getConfig(DOWNLOAD_URL);
        String command = getConfig(COMMAND);

        String downloadName = DOWNLOAD_URL.getName();
        String commandName = COMMAND.getName();

        if (!(isNonBlank(downloadUrl) ^ isNonBlank(command))) {
            throw illegal("Must specify exactly one of", downloadName, "and", commandName);
        }

        if (isNonBlank(downloadUrl)) {
            String scriptDir = getConfig(SCRIPT_DIR);
            String scriptPath = calculateDestPath(downloadUrl, scriptDir);
            result = executeDownloadedScript(machineLocation, downloadUrl, scriptPath);
        }

        if (isNonBlank(command)) {
            result = executeShellCommand(machineLocation, command);
        }

        handle(result);
    }

    private SimpleShellCommand.Result executeDownloadedScript(MachineLocation machineLocation, String url, String scriptPath) {

        SshMachineLocation machine = getSshMachine(ImmutableList.<Location>of(machineLocation));

        TaskFactory<?> install = SshTasks.installFromUrl(ImmutableMap.<String, Object>of(), machine, url, scriptPath);
        DynamicTasks.queue(install);
        DynamicTasks.waitForLast();

        List<String> commands = new ArrayList<>();
        commands.add("chmod u+x " + scriptPath);
        maybeCdToRunDir(commands);
        commands.add(scriptPath);

        return runCommands(machine, commands);
    }

    private SimpleShellCommand.Result executeShellCommand(MachineLocation machineLocation, String command) {

        SshMachineLocation machine = getSshMachine(ImmutableList.of(machineLocation));

        List<String> commands = new ArrayList<>();
        maybeCdToRunDir(commands);
        commands.add(command);

        return runCommands(machine, commands);
    }

    private void maybeCdToRunDir(List<String> commands) {
        String runDir = getConfig(RUN_DIR);
        if (!isBlank(runDir)) {
            commands.add(CD + " " + runDir);
        }
    }

    private Result runCommands(SshMachineLocation machine, List<String> commands) {
        SshEffectorTasks.SshEffectorTaskFactory<Integer> etf = SshEffectorTasks.ssh(machine, commands.toArray(new String[]{}));

        ProcessTaskWrapper<Integer> job = DynamicTasks.queue(etf);
        DynamicTasks.waitForLast();
        return buildResult(job);
    }

    private <T> SimpleShellCommand.Result buildResult(final ProcessTaskWrapper<Integer> job) {
        final int exitCode = job.get();
        final String stdout = job.getStdout().trim();
        final String stderr = job.getStderr().trim();
        return new SimpleShellCommand.Result() {

            @Override
            public int getExitCode() {
                return exitCode;
            }

            @Override
            public String getStdout() {
                return stdout;
            }

            @Override
            public String getStderr() {
                return stderr;
            }
        };
    }

    private IllegalArgumentException illegal(String message, String ...messages) {
        return new IllegalArgumentException(Joiner.on(' ').join(this.toString() + ":", message, messages));
    }

    private SshMachineLocation getSshMachine(Collection<? extends Location> hostLocations) {
        Maybe<SshMachineLocation> host = Locations.findUniqueSshMachineLocation(hostLocations);
        if (host.isAbsent()) {
            throw new IllegalArgumentException("No SSH machine found to run command");
        }
        return host.get();
    }

    private String calculateDestPath(String url, String directory) {
        try {
            URL asUrl = new URL(url);
            Iterable<String> path = Splitter.on("/").split(asUrl.getPath());
            String scriptName = getLastPartOfPath(path, DEFAULT_NAME);
            return Joiner.on("/").join(directory, "test-" + randomDir(), scriptName);
        } catch (MalformedURLException e) {
            throw illegal("Malformed URL:", url);
        }
    }

    private String randomDir() {
        return Integer.valueOf(new Random(System.currentTimeMillis()).nextInt(100000)).toString();
    }

    private static String getLastPartOfPath(Iterable<String> path, String defaultName) {
        MutableList<String> parts = MutableList.copyOf(path);
        Collections.reverse(parts);
        Iterator<String> it = parts.iterator();
        String scriptName = null;

        // strip any trailing "/" parts of URL
        while (isBlank(scriptName) && it.hasNext()) {
            scriptName = it.next();
        }
        if (isBlank(scriptName)) {
            scriptName = defaultName;
        }
        return scriptName;
    }

}
