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
package org.apache.brooklyn.util.core.internal.winrm.winrm4j;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.config.Sanitizer;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.internal.winrm.WinRmException;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import io.cloudsoft.winrm4j.winrm.WinRmTool;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;

@Beta
public class Winrm4jTool implements org.apache.brooklyn.util.core.internal.winrm.WinRmTool {

    private static final Logger LOG = LoggerFactory.getLogger(Winrm4jTool.class);

    // TODO Should we move this up to the interface?
    @Beta
    public static final ConfigKey<Boolean> LOG_CREDENTIALS = ConfigKeys.newBooleanConfigKey(
            "logCredentials", 
            "Whether to log the WinRM credentials used - strongly recommended never be used in production, as it is a big security hole!",
            false);

    private final ConfigBag bag;
    private final String host;
    private final Integer port;
    private final String user;
    private final String password;
    private final int execTries;
    private final Duration execRetryDelay;
    private final boolean logCredentials;
    
    public Winrm4jTool(Map<String,?> config) {
        this(ConfigBag.newInstance(config));
    }
    
    public Winrm4jTool(ConfigBag config) {
        this.bag = checkNotNull(config, "config bag");
        host = getRequiredConfig(config, PROP_HOST);
        port = getRequiredConfig(config, PROP_PORT);
        user = getRequiredConfig(config, PROP_USER);
        password = getRequiredConfig(config, PROP_PASSWORD);
        execTries = getRequiredConfig(config, PROP_EXEC_TRIES);
        execRetryDelay = getRequiredConfig(config, PROP_EXEC_RETRY_DELAY);
        logCredentials = getRequiredConfig(config, LOG_CREDENTIALS);
    }
    
    @Override
    public org.apache.brooklyn.util.core.internal.winrm.WinRmToolResponse executeCommand(final List<String> commands) {
        return exec(new Function<io.cloudsoft.winrm4j.winrm.WinRmTool, io.cloudsoft.winrm4j.winrm.WinRmToolResponse>() {
            @Override public WinRmToolResponse apply(io.cloudsoft.winrm4j.winrm.WinRmTool tool) {
                return tool.executeCommand(commands);
            }
        });
    }

    @Override
    @Deprecated
    public org.apache.brooklyn.util.core.internal.winrm.WinRmToolResponse executeScript(final List<String> commands) {
        return executeCommand(commands);
    }
    
    @Override
    public org.apache.brooklyn.util.core.internal.winrm.WinRmToolResponse executePs(final List<String> commands) {
        return exec(new Function<io.cloudsoft.winrm4j.winrm.WinRmTool, io.cloudsoft.winrm4j.winrm.WinRmToolResponse>() {
            @Override public WinRmToolResponse apply(io.cloudsoft.winrm4j.winrm.WinRmTool tool) {
                return tool.executePs(commands);
            }
        });
    }

    @Override
    public org.apache.brooklyn.util.core.internal.winrm.WinRmToolResponse copyToServer(InputStream source, String destination) {
        executePs(ImmutableList.of("rm -ErrorAction SilentlyContinue " + destination));
        try {
            int chunkSize = getRequiredConfig(bag, COPY_FILE_CHUNK_SIZE_BYTES);
            byte[] inputData = new byte[chunkSize];
            int bytesRead;
            int expectedFileSize = 0;
            while ((bytesRead = source.read(inputData)) > 0) {
                byte[] chunk;
                if (bytesRead == chunkSize) {
                    chunk = inputData;
                } else {
                    chunk = Arrays.copyOf(inputData, bytesRead);
                }
                executePs(ImmutableList.of("If ((!(Test-Path " + destination + ")) -or ((Get-Item '" + destination + "').length -eq " +
                        expectedFileSize + ")) {Add-Content -Encoding Byte -path " + destination +
                        " -value ([System.Convert]::FromBase64String(\"" + new String(Base64.encodeBase64(chunk)) + "\"))}"));
                expectedFileSize += bytesRead;
            }

            return new org.apache.brooklyn.util.core.internal.winrm.WinRmToolResponse("", "", 0);
        } catch (java.io.IOException e) {
            throw propagate(e, "Failed copying to server at "+destination);
        }
    }

    private org.apache.brooklyn.util.core.internal.winrm.WinRmToolResponse exec(Function<io.cloudsoft.winrm4j.winrm.WinRmTool, io.cloudsoft.winrm4j.winrm.WinRmToolResponse> task) {
        Collection<Throwable> exceptions = Lists.newArrayList();
        Stopwatch totalStopwatch = Stopwatch.createStarted();
        
        for (int i = 0; i < execTries; i++) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            Duration connectTimestamp = null;
            Duration execTimestamp = null;
            try {
                WinRmTool tool = connect();
                connectTimestamp = Duration.of(stopwatch);
                WinRmToolResponse result = task.apply(tool);
                execTimestamp = Duration.of(stopwatch);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Finished WinRM exec on "+user+"@"+host+":"+port+" "
                            + (logCredentials ? "password=" + password : "")
                            + " done after "+Duration.of(execTimestamp).toStringRounded()
                            + " (connected in "+Duration.of(connectTimestamp).toStringRounded() + ")");
                }
                return wrap(result);
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                Duration sleep = Duration.millis(Math.min(Math.pow(2, i) * 1000, execRetryDelay.toMilliseconds()));
                Duration failTimestamp = Duration.of(stopwatch);
                String timeMsg = "total time "+Duration.of(totalStopwatch).toStringRounded()
                        + ", this attempt failed after "+Duration.of(failTimestamp).toStringRounded()
                        + (connectTimestamp != null ? ", connected in "+Duration.of(connectTimestamp).toStringRounded() : "");
                
                if ((i + 1) == execTries) {
                    LOG.info("Propagating exception - WinRM failed on "+user+"@"+host+":"+port+" "
                            + (logCredentials ? "password=" + password : "")
                            + "; (attempt "+(i+1)+" of "+execTries+"; "+timeMsg+")", e);
                } else if (i == 0) {
                    LOG.warn("Ignoring WinRM exception on "+user+"@"+host+":"+port+" "
                            + (logCredentials ? "password=" + password : "")
                            + " and will retry after "+sleep+" (attempt "+(i+1)+" of "+execTries+"; "+timeMsg+")", e);
                    Time.sleep(sleep);
                } else {
                    LOG.debug("Ignoring WinRM exception on "+user+"@"+host+":"+port+" "
                            + (logCredentials ? "password=" + password : "")
                            + " and will retry after "+sleep+" (attempt "+(i+1)+" of "+execTries+"; "+timeMsg+")", e);
                    Time.sleep(sleep);
                }
                exceptions.add(e);
            }
        }
        throw propagate(Exceptions.create("failed to execute command", exceptions), "");
    }

    private io.cloudsoft.winrm4j.winrm.WinRmTool connect() {
        return io.cloudsoft.winrm4j.winrm.WinRmTool.connect(host+":"+port, user, password);
    }
    
    private <T> T getRequiredConfig(ConfigBag bag, ConfigKey<T> key) {
        T result = bag.get(key);
        if (result == null) {
            throw new IllegalArgumentException("Missing config "+key+" in "+Sanitizer.sanitize(bag));
        }
        return result;
    }
    
    private org.apache.brooklyn.util.core.internal.winrm.WinRmToolResponse wrap(io.cloudsoft.winrm4j.winrm.WinRmToolResponse resp) {
        return new org.apache.brooklyn.util.core.internal.winrm.WinRmToolResponse(resp.getStdOut(), resp.getStdErr(), resp.getStatusCode());
    }

    @Override
    public String toString() {
        return String.format("%s@%s:%d", user, host, port);
    }
    
    /**
     * @throws WinRmException If the given {@code e} is not fatal (e.g. not an {@link Error} or {@link InterruptedException},
     *         then wraps it in a {@link WinRmException}.
     */
    protected WinRmException propagate(Exception e, String message) throws WinRmException {
        Exceptions.propagateIfFatal(e);
        throw new WinRmException("(" + toString() + ") " + message + ": " + e.getMessage(), e);
    }
}
