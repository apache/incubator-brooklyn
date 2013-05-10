package brooklyn.util;

import brooklyn.util.internal.StreamGobbler;
import com.google.common.base.Throwables;
import groovy.io.GroovyPrintStream;
import groovy.time.TimeDuration;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Joiner.on;

public class ShellUtils {

    private static final int TIMEOUT_MS = 60 * 1000;


    /**
     * as {@link ShellUtils.exec(String[], String[], File, String, Logger)} but uses `bash -l -c ${cmd}'
     * (to have a good PATH set), and defaults for other fields;
     * requires a logger and a context object (whose toString is used in the logger and in error messages);
     * optionally takes a string to use as input to the command
     */
    public static String[] exec(String cmd, String input, Logger log, Object context) {
        return exec(new String[]{"bash", "-l", "-c", cmd}, null, null, input, log, context);
    }

    public static String[] exec(String cmd, Logger log, Object context) {
        return exec(cmd, null, log, context);
    }

    public static String[] exec(Map flags, String cmd, String input, Logger log, Object context) {
        return exec(flags, new String[]{"bash", "-l", "-c", cmd}, null, null, input, log, context);
    }

    public static String[] exec(Map flags, String cmd, Logger log, Object context) {
        return exec(flags, cmd, null, log, context);
    }

    public static String[] exec(String[] cmd, String[] envp, File dir, String input, Logger log, Object context) {
        return exec(Collections.EMPTY_MAP, cmd, envp, dir, input, log, context);
    }

    private static long getTimeoutMs(Map flags) {
        long timeout = TIMEOUT_MS;

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
     * executes the single given command (words) with given environment (inherited if null)
     * and cwd (. if null), feeding it the given input stream (if not null).
     * logs I/O at debug (if not null).
     * throws exception if return code non-zero, otherwise returns lines from stdout.
     * <p/>
     * flags:  timeout (TimeDuration), 0 for forever; default 60 seconds
     */
    public static String[] exec(Map flags, final String[] cmd, String[] envp, File dir, String input, final Logger log, final Object context) {
        if (log.isDebugEnabled()) {
            log.debug("Running local command: {} {}", context, on("").join(cmd));
        }

        try {
            final Process process = Runtime.getRuntime().exec(cmd, envp, dir);
            ByteArrayOutputStream stdoutB = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrB = new ByteArrayOutputStream();
            PrintStream stdoutP = new GroovyPrintStream(stdoutB);
            PrintStream stderrP = new GroovyPrintStream(stderrB);
            StreamGobbler stdoutG = new StreamGobbler(process.getInputStream(), stdoutP, log).setLogPrefix("[" + context + ":stdout] ");
            stdoutG.start();
            StreamGobbler stderrG = new StreamGobbler(process.getErrorStream(), stderrP, log).setLogPrefix("[" + context + ":stderr] ");
            stderrG.start();
            if (input != null) {
                process.getOutputStream().write(input.getBytes());
                process.getOutputStream().flush();
            }

            final long timeout = getTimeoutMs(flags);
            final AtomicBoolean ended = new AtomicBoolean(false);
            final AtomicBoolean killed = new AtomicBoolean(false);

            //if a timeout was specified, this thread will kill the process. This is a work around because the process.waitFor'
            //doesn't accept a timeout.
            Thread timeoutThread = new Thread() {
                public void run() {
                    if (timeout <= 0) return;
                    try {
                        sleep(timeout);
                        if (!ended.get()) {
                            if (log.isDebugEnabled()) {
                                log.debug("Timeout exceeded for {} {}", context, on("").join(cmd));
                            }
                            process.destroy();
                            killed.set(true);
                        }
                    } catch (Exception e) {
                    }
                }
            };

            if (timeout > 0) timeoutThread.start();
            int exitCode = process.waitFor();
            ended.set(true);
            if (timeout > 0) timeoutThread.interrupt();

            stdoutG.blockUntilFinished();
            stderrG.blockUntilFinished();
            if (exitCode != 0 || killed.get()) {
                String message = killed.get() ? "terminated after timeout" : "exit code "+exitCode;
                if (log.isDebugEnabled()) {
                    log.debug("Completed local command (problem, throwing): {} {}; {}", new Object[]{context, on("").join(cmd), message});
                }
                String e = "Command failed ("+message+"): " + on(" ").join(cmd);
                log.warn(e + "\n" + stdoutB + (stderrB.size() > 0 ? "\n--\n" + stderrB : ""));
                throw new IllegalStateException(e + " (details logged)");
            }
            if(log.isDebugEnabled()){
                log.debug("Completed local command: {} {}; exit code 0", context, on("").join(cmd));
            }
            return stdoutB.toString().split("\n");
        } catch (IOException e) {
           throw Throwables.propagate(e);
        } catch (InterruptedException e) {
            throw Throwables.propagate(e);
        }
    }
}
