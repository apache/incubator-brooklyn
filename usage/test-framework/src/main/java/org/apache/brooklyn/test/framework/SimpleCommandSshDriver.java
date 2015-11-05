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
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.TaskAdaptable;
import org.apache.brooklyn.api.mgmt.TaskFactory;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskFactory;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.FatalRuntimeException;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Random;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.brooklyn.util.text.Strings.isBlank;

/**
 * Driver for {@link SimpleCommand}.
 */
public class SimpleCommandSshDriver implements SimpleCommandDriver {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleCommandSshDriver.class);
    public static final String DEFAULT_NAME = "download.sh";

    protected final EntityLocal entity;
    protected final ResourceUtils resource;
    protected final Location location;

    public SimpleCommandSshDriver(EntityLocal entity, SshMachineLocation location) {
        LOG.debug("Constructing SSH driver for simple command for {} at {}", entity, location);
        this.entity = checkNotNull(entity, "entity");
        this.location = checkNotNull(location, "location");
        this.resource = ResourceUtils.create(entity);
    }

    @Override
    public EntityLocal getEntity() {
        return entity;
    }

    @Override
    public void start() {
        LOG.debug("Performing start in SSH driver for simple command");
        invoke();
    }

    private void invoke() {
        SimpleCommand simpleCommand = (SimpleCommand) getEntity();
        simpleCommand.start(ImmutableList.of(location));
    }

    @Override
    public void restart() {
        LOG.debug("Performing restart in SSH driver for simple command");
        invoke();
    }

    @Override
    public void stop() {
        LOG.debug("Performing stop in SSH driver for simple command");
    }

    @Override
    public Result execute(Collection<? extends Location> hostLocations, String command) {

        SshMachineLocation machine = getSshMachine(hostLocations);
        ProcessTaskFactory<Integer> taskFactory = SshTasks.newSshExecTaskFactory(machine, command);

        LOG.debug("Creating task to execute '{}' on location {}", command, machine);
        final ProcessTaskWrapper<Integer> job = DynamicTasks.queue(taskFactory);
        DynamicTasks.waitForLast();
        return buildResult(job);
    }

    private <T> Result buildResult(final ProcessTaskWrapper<Integer> job) {
        return new Result() {

            @Override
            public int getExitCode() {
                return job.get();
            }

            @Override
            public String getStdout() {
                return job.getStdout().trim();
            }

            @Override
            public String getStderr() {
                return job.getStderr().trim();
            }
        };
    }

    @Override
    public Result executeDownloadedScript(Collection<? extends Location> hostLocations,
                                          String url, String directory) {

        SshMachineLocation machine = getSshMachine(hostLocations);
        String destPath = calculateDestPath(url, directory);

        TaskFactory<?> install = SshTasks.installFromUrl(ImmutableMap.<String, Object>of(), machine, url, destPath);
        DynamicTasks.queue(install);
        DynamicTasks.waitForLast();

        machine.execCommands("make the script executable", ImmutableList.<String>of("chmod u+x " + destPath));

        return execute(hostLocations, destPath);
    }

    private String calculateDestPath(String url, String directory) {
        try {
            URL asUrl = new URL(url);
            Iterable<String> path = Splitter.on("/").split(asUrl.getPath());
            String scriptName = getLastPartOfPath(path, DEFAULT_NAME);
            return Joiner.on("/").join(directory, "test-" + randomDir(), scriptName);
        } catch (MalformedURLException e) {
            throw Exceptions.propagate(new FatalRuntimeException("Malformed URL: " + url));
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

    private SshMachineLocation getSshMachine(Collection<? extends Location> hostLocations) {
        Maybe<SshMachineLocation> host = Locations.findUniqueSshMachineLocation(hostLocations);
        if (host.isAbsent()) {
            throw new IllegalArgumentException("No SSH machine found to run command");
        }
        return host.get();
    }

    @Override
    public Location getLocation() {
        return location;
    }

}