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
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.TaskFactory;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static org.apache.brooklyn.core.entity.lifecycle.Lifecycle.*;
import static org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.setExpectedState;
import static org.apache.brooklyn.util.groovy.GroovyJavaMethods.truth;
import static org.apache.brooklyn.util.text.Strings.isBlank;
import static org.apache.brooklyn.util.text.Strings.isNonBlank;
import static org.apache.commons.collections.MapUtils.isEmpty;

public class SimpleShellCommandTestImpl extends AbstractTest implements SimpleShellCommandTest {

    public static final int SUCCESS = 0;

    private static final Logger LOG = LoggerFactory.getLogger(SimpleShellCommandTestImpl.class);
    private static final int A_LINE = 80;
    public static final String DEFAULT_NAME = "download.sh";
    private static final String CD = "cd";

    @Override
    public void start(Collection<? extends Location> locations) {
        setExpectedState(this, STARTING);
        execute();
    }

    @Override
    public void stop() {
        LOG.debug("{} Stopping simple command", this);
        setUpAndRunState(false, STOPPED);
    }

    @Override
    public void restart() {
        LOG.debug("{} Restarting simple command", this);
        execute();
    }

    private void setUpAndRunState(boolean up, Lifecycle status) {
        sensors().set(SERVICE_UP, up);
        setExpectedState(this, status);
    }

    private static class Result {
        int exitCode;
        String stdout;
        String stderr;
        public Result(final ProcessTaskWrapper<Integer> job) {
            exitCode = job.get();
            stdout = job.getStdout().trim();
            stderr = job.getStderr().trim();
        }
        public int getExitCode() {
            return exitCode;
        }
        public String getStdout() {
            return stdout;
        }
        public String getStderr() {
            return stderr;
        }
    }

    protected void handle(Result result) {
        LOG.debug("{}, Result is {}\nwith output [\n{}\n] and error [\n{}\n]", new Object[] {
            this, result.getExitCode(), shorten(result.getStdout()), shorten(result.getStderr())
        });
        AssertionSupport support = new AssertionSupport();
        checkAssertions(support, exitCodeAssertions(), "exit code", result.getExitCode());
        checkAssertions(support, getConfig(ASSERT_OUT), "stdout", result.getStdout());
        checkAssertions(support, getConfig(ASSERT_ERR), "stderr", result.getStderr());
        support.validate();
    }

    private String shorten(String text) {
        return Strings.maxlenWithEllipsis(text, A_LINE);
    }

    public void execute() {
        try {
            SshMachineLocation machineLocation =
                Machines.findUniqueMachineLocation(resolveTarget().getLocations(), SshMachineLocation.class).get();
            executeCommand(machineLocation);
            setUpAndRunState(true, RUNNING);
        } catch (Throwable t) {
            setUpAndRunState(false, ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    private void executeCommand(SshMachineLocation machineLocation) {

        Result result = null;
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

    private Result executeDownloadedScript(SshMachineLocation machineLocation, String url, String scriptPath) {

        TaskFactory<?> install = SshTasks.installFromUrl(ImmutableMap.<String, Object>of(), machineLocation, url, scriptPath);
        DynamicTasks.queue(install);
        DynamicTasks.waitForLast();

        List<String> commands = new ArrayList<>();
        commands.add("chmod u+x " + scriptPath);
        maybeCdToRunDir(commands);
        commands.add(scriptPath);

        return runCommands(machineLocation, commands);
    }

    private Result executeShellCommand(SshMachineLocation machineLocation, String command) {

        List<String> commands = new ArrayList<>();
        maybeCdToRunDir(commands);
        commands.add(command);

        return runCommands(machineLocation, commands);
    }

    private void maybeCdToRunDir(List<String> commands) {
        String runDir = getConfig(RUN_DIR);
        if (!isBlank(runDir)) {
            commands.add(CD + " " + runDir);
        }
    }

    private Result runCommands(SshMachineLocation machine, List<String> commands) {
        SshEffectorTasks.SshEffectorTaskFactory<Integer> etf = SshEffectorTasks.ssh(commands.toArray(new String[]{}))
            .machine(machine);

        ProcessTaskWrapper<Integer> job = DynamicTasks.queue(etf);
        job.asTask().blockUntilEnded();
        return new Result(job);
    }



    private IllegalArgumentException illegal(String message, String ...messages) {
        return new IllegalArgumentException(Joiner.on(' ').join(this.toString() + ":", message, messages));
    }

    private String calculateDestPath(String url, String directory) {
        try {
            URL asUrl = new URL(url);
            Iterable<String> path = Splitter.on("/").split(asUrl.getPath());
            String scriptName = getLastPartOfPath(path, DEFAULT_NAME);
            return Joiner.on("/").join(directory, "test-" + Identifiers.makeRandomId(8), scriptName);
        } catch (MalformedURLException e) {
            throw illegal("Malformed URL:", url);
        }
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
    
    private <T> void checkAssertions(AssertionSupport support, Map<?, ?> assertions, String target, T actual) {
        if (null == assertions) {
            return;
        }
        if (null == actual) {
            support.fail(target, "no actual value", "");
            return;
        }
        for (Map.Entry<?, ?> assertion : assertions.entrySet()) {
            String condition = assertion.getKey().toString();
            Object expected = assertion.getValue();
            switch (condition) {
                case EQUALS :
                    if (!actual.equals(expected)) {
                        support.fail(target, EQUALS, expected);
                    }
                    break;
                case CONTAINS :
                    if (!actual.toString().contains(expected.toString())) {
                        support.fail(target, CONTAINS, expected);
                    }
                    break;
                case IS_EMPTY:
                    if (!actual.toString().isEmpty() && truth(expected)) {
                        support.fail(target, IS_EMPTY, expected);
                    }
                    break;
                case MATCHES :
                    if (!actual.toString().matches(expected.toString())) {
                        support.fail(target, MATCHES, expected);
                    }
                    break;
                default:
                    support.fail(target, "unknown condition", condition);
            }
        }
    }

    private Map<?, ?> exitCodeAssertions() {
        Map<?, ?> assertStatus = getConfig(ASSERT_STATUS);
        if (isEmpty(assertStatus)) {
            assertStatus = ImmutableMap.of(EQUALS, SUCCESS);
        }
        return assertStatus;
    }

    public static class FailedAssertion {
        String target;
        String assertion;
        String expected;

        public FailedAssertion(String target, String assertion, String expected) {
            this.target = target;
            this.assertion = assertion;
            this.expected = expected;
        }
        public String description() {
            return Joiner.on(' ').join(target, assertion, expected);
        }
    }

    /**
     * A convenience to collect and validate any assertion failures.
     */
    public static class AssertionSupport {
        private List<FailedAssertion> failures = new ArrayList<>();

        public void fail(String target, String assertion, Object expected) {
            failures.add(new FailedAssertion(target, assertion, expected.toString()));
        }

        /**
         * @throws AssertionError if any failures were collected.
         */
        public void validate() {
            if (0 < failures.size()) {
                StringBuilder summary = new StringBuilder();
                summary.append("Assertion Failures: \n");
                for (FailedAssertion fail : failures) {
                    summary.append(fail.description()).append("\n");
                }
                Asserts.fail(summary.toString());
            }
        }
    }
}
