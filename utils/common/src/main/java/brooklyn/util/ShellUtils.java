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
package brooklyn.util;

import groovy.io.GroovyPrintStream;
import groovy.time.TimeDuration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.stream.StreamGobbler;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.Strings;

import com.google.common.collect.Maps;
import com.google.common.io.Closer;

/**
 * @deprecated since 0.7; does not return exit status, stderr, etc, so utility is of very limited use; and is not used in core brooklyn at all!;
 * use ProcessTool or SystemProcessTaskFactory.
 */
@Deprecated
public class ShellUtils {

    public static long TIMEOUT = 60*1000;

    /**
     * Executes the given command.
     * <p>
     * Uses {@code bash -l -c cmd} (to have a good PATH set), and defaults for other fields.
     * <p>
     * requires a logger and a context object (whose toString is used in the logger and in error messages)
     * optionally takes a string to use as input to the command
     *
     * @see {@link #exec(String, String, Logger, Object)}
     */
    public static String[] exec(String cmd, Logger log, Object context) {
        return exec(cmd, null, log, context);
    }
    /** @see {@link #exec(String[], String[], File, String, Logger, Object)} */
    public static String[] exec(String cmd, String input, Logger log, Object context) {
        return exec(new String[] { "bash", "-l", "-c", cmd }, null, null, input, log, context);
    }
    /** @see {@link #exec(Map, String[], String[], File, String, Logger, Object)} */
    public static String[] exec(Map flags, String cmd, Logger log, Object context) {
        return exec(flags, new String[] { "bash", "-l", "-c", cmd }, null, null, null, log, context);
    }
    /** @see {@link #exec(Map, String[], String[], File, String, Logger, Object)} */
    public static String[] exec(Map flags, String cmd, String input, Logger log, Object context) {
        return exec(flags, new String[] { "bash", "-l", "-c", cmd }, null, null, input, log, context);
    }
    /** @see {@link #exec(Map, String[], String[], File, String, Logger, Object)} */
    public static String[] exec(String[] cmd, String[] envp, File dir, String input, Logger log, Object context) {
        return exec(Maps.newLinkedHashMap(), cmd, envp, dir, input, log, context);
    }

    private static long getTimeoutMs(Map flags) {
        long timeout = TIMEOUT;

        Object tf = flags.get("timeout");

        if (tf instanceof Number) {
            timeout = ((Number) tf).longValue();
        } else if (tf instanceof TimeDuration) {
            timeout = ((TimeDuration) tf).toMilliseconds();
        }

        //if (tf != null) timeout = tf;

        return timeout;
    }

    /**
     * Executes the given command.
     * <p>
     * Uses the given environmnet (inherited if null) and cwd ({@literal .} if null),
     * feeding it the given input stream (if not null) and logging I/O at debug (if not null).
     * <p>
     * flags:  timeout (Duration), 0 for forever; default 60 seconds
     *
     * @throws IllegalStateException if return code non-zero
     * @return lines from stdout.
     */
    public static String[] exec(Map flags, final String[] cmd, String[] envp, File dir, String input, final Logger log, final Object context) {
        if (log.isDebugEnabled()) {
            log.debug("Running local command: {}% {}", context, Strings.join(cmd, " "));
        }
        Closer closer = Closer.create();
        try {
            final Process proc = Runtime.getRuntime().exec(cmd, envp, dir); // Call *execute* on the string
            ByteArrayOutputStream stdoutB = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrB = new ByteArrayOutputStream();
            PrintStream stdoutP = new GroovyPrintStream(stdoutB);
            PrintStream stderrP = new GroovyPrintStream(stderrB);
            @SuppressWarnings("resource")
            StreamGobbler stdoutG = new StreamGobbler(proc.getInputStream(), stdoutP, log).setLogPrefix("["+context+":stdout] ");
            stdoutG.start();
            closer.register(stdoutG);
            @SuppressWarnings("resource")
            StreamGobbler stderrG = new StreamGobbler(proc.getErrorStream(), stderrP, log).setLogPrefix("["+context+":stderr] ");
            stderrG.start();
            closer.register(stderrG);
            if (input!=null && input.length()>0) {
                proc.getOutputStream().write(input.getBytes());
                proc.getOutputStream().flush();
            }

            final long timeout = getTimeoutMs(flags);
            final AtomicBoolean ended = new AtomicBoolean(false);
            final AtomicBoolean killed = new AtomicBoolean(false);

            //if a timeout was specified, this thread will kill the process. This is a work around because the process.waitFor'
            //doesn't accept a timeout.
            Thread timeoutThread = new Thread(new Runnable() {
                public void run() {
                    if (timeout <= 0) return;
                    try { 
                        Thread.sleep(timeout);
                        if (!ended.get()) {
                            if (log.isDebugEnabled()) {
                                log.debug("Timeout exceeded for "+context+"% "+Strings.join(cmd, " "));
                            }
                            proc.destroy();
                            killed.set(true);
                        }
                    } catch (Exception e) { }
                }
            });
            if (timeout > 0) timeoutThread.start();
            int exitCode = proc.waitFor();
            ended.set(true);
            if (timeout > 0) timeoutThread.interrupt();

            stdoutG.blockUntilFinished();
            stderrG.blockUntilFinished();
            if (exitCode!=0 || killed.get()) {
                String message = killed.get() ? "terminated after timeout" : "exit code "+exitCode;
                if (log.isDebugEnabled()) {
                    log.debug("Completed local command (problem, throwing): "+context+"% "+Strings.join(cmd, " ")+" - "+message);
                }
                String e = "Command failed ("+message+"): "+Strings.join(cmd, " ");
                log.warn(e+"\n"+stdoutB+(stderrB.size()>0 ? "\n--\n"+stderrB : ""));
                throw new IllegalStateException(e+" (details logged)");
            }
            if (log.isDebugEnabled()) {
                log.debug("Completed local command: "+context+"% "+Strings.join(cmd, " ")+" - exit code 0");
            }
            return stdoutB.toString().split("\n");
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } finally {
            Streams.closeQuietly(closer);
        }
    }

}
