package brooklyn.util;

import groovy.io.GroovyPrintStream;
import groovy.time.TimeDuration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.stream.StreamGobbler;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.collect.Maps;
import com.google.common.io.Closer;

public class ShellUtils {

    private static final Logger log = LoggerFactory.getLogger(ShellUtils.class);
    
    public static long TIMEOUT = 60*1000;
    
    /** as {@link #exec(String[], String[], File, String, Logger)} but uses `bash -l -c ${cmd}' 
     * (to have a good PATH set), and defaults for other fields; 
     * requires a logger and a context object (whose toString is used in the logger and in error messages);
     * optionally takes a string to use as input to the command
     */
    public static String[] exec(String cmd, Logger log, Object context) {
        return exec(cmd, null, log, context);
    }
    /** see {@link #exec(String, Logger, Object)} */
    public static String[] exec(String cmd, String input, Logger log, Object context) {
        return exec(new String[] { "bash", "-l", "-c", cmd }, null, null, input, log, context);
    }
    /** see {@link #exec(String, Logger, Object)} */
    public static String[] exec(Map flags , String cmd, Logger log, Object context) {
        return exec(flags, new String[] { "bash", "-l", "-c", cmd }, null, null, null, log, context);
    }
    /** see {@link #exec(String, Logger, Object)} */
    public static String[] exec(Map flags , String cmd, String input, Logger log, Object context) {
        return exec(flags, new String[] { "bash", "-l", "-c", cmd }, null, null, input, log, context);
    }
    /** see {@link #exec(String, Logger, Object)} */
    public static String[] exec(String[] cmd, String[] envp, File dir, String input, Logger log, Object context) {
        return exec(Maps.newLinkedHashMap(), cmd, envp, dir, input, log, context);
    }
        
    /** executes the single given command (words) with given environmnet (inherited if null)
     * and cwd (. if null), feeding it the given input stream (if not null).
     * logs I/O at debug (if not null).
     * throws exception if return code non-zero, otherwise returns lines from stdout.
     * <p>
     * flags:  timeout (Duration), 0 for forever; default 60 seconds
     */
    public static String[] exec(Map flags, final String[] cmd, String[] envp, File dir, String input, final Logger log, final Object context) {
        Closer closer = Closer.create();
        try {
            log.debug("Running local command: "+context+"% "+Strings.join(cmd, " "));
            final Process proc = Runtime.getRuntime().exec(cmd, envp, dir);                 // Call *execute* on the string
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
            long timeout2 = TIMEOUT;
            Object tf = flags.get("timeout");
            if (tf instanceof Number) timeout2=((Number)tf).longValue();
            else if (tf instanceof Duration) timeout2=((Duration)tf).toMilliseconds();
            else if (tf instanceof TimeDuration) timeout2=((TimeDuration)tf).toMilliseconds();
            if (tf!=null) timeout2 = (Long)tf;
            final long timeout = timeout2;
            
            final AtomicBoolean ended = new AtomicBoolean(false);
            final AtomicBoolean killed = new AtomicBoolean(false);
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try { 
                if (timeout>0) {
                    Thread.sleep(timeout);
                    if (!ended.get()) {
                            log.debug("Timeout exceeded for "+context+"% "+Strings.join(cmd, " "));
                            proc.destroy();
                            killed.set(true);
                    }
                } 
                    } catch (Exception e) {} }});
            if (TIMEOUT>0) t.start();
            int exitCode = proc.waitFor();
            ended.set(true);
            if (TIMEOUT>0) t.interrupt();
            
            stdoutG.blockUntilFinished();
            stderrG.blockUntilFinished();
            if (exitCode!=0 || killed.get()) {
                String message = killed.get() ? "terminated after timeout" : "exit code "+exitCode;
                log.debug("Completed local command (problem, throwing): "+context+"% "+Strings.join(cmd, " ")+" - "+message);
                String e = "Command failed ("+message+"): "+Strings.join(cmd, " ");
                log.warn(e+"\n"+stdoutB+(stderrB.size()>0 ? "\n--\n"+stderrB : ""));
                throw new IllegalStateException(e+" (details logged)");
            }
            log.debug("Completed local command: "+context+"% "+Strings.join(cmd, " ")+" - exit code 0");
            return stdoutB.toString().split("\n");
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        } finally {
            Streams.closeQuietly(closer);
        }
    }

}
