package brooklyn.util

import groovy.io.GroovyPrintStream
import groovy.time.TimeDuration;

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import org.slf4j.Logger

import brooklyn.util.internal.StreamGobbler

class ShellUtils {

    public static TIMEOUT = 60*1000;

    
    /** as {@link #exec(String[], String[], File, String, Logger)} but uses `bash -l -c ${cmd}' 
     * (to have a good PATH set), and defaults for other fields; 
     * requires a logger and a context object (whose toString is used in the logger and in error messages);
     * optionally takes a string to use as input to the command
     */
    public static String[] exec(String cmd, String input=null, Logger log, Object context) {
        exec(["bash", "-l", "-c", cmd] as String[], null, null, input, log, context);
    }

    public static String[] exec(Map flags , String cmd, String input=null, Logger log, Object context) {
        exec(flags, ["bash", "-l", "-c", cmd] as String[], null, null, input, log, context);
    }

    public static String[] exec(String[] cmd, String[] envp, File dir, String input, Logger log, Object context) {
        exec([:], cmd, envp, dir, input, log, context);
    }
        
    /** executes the single given command (words) with given environmnet (inherited if null)
     * and cwd (. if null), feeding it the given input stream (if not null).
     * logs I/O at debug (if not null).
     * throws exception if return code non-zero, otherwise returns lines from stdout.
     * <p>
     * flags:  timeout (TimeDuration), 0 for forever; default 60 seconds
     */
    public static String[] exec(Map flags, String[] cmd, String[] envp, File dir, String input, Logger log, Object context) {
        log.debug("Running local command: $context% ${cmd.join(" ")}");
        Process proc = cmd.execute(envp, dir);                 // Call *execute* on the string
        ByteArrayOutputStream stdoutB = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrB = new ByteArrayOutputStream();
        PrintStream stdoutP = new GroovyPrintStream(stdoutB);
        PrintStream stderrP = new GroovyPrintStream(stderrB);
        def stdoutG = new StreamGobbler(proc.inputStream, stdoutP, log).setLogPrefix("["+context+":stdout] ");
        stdoutG.start()
        def stderrG = new StreamGobbler(proc.errorStream, stderrP, log).setLogPrefix("["+context+":stderr] ");
        stderrG.start()
        if (input) {
            proc.getOutputStream().write(input.getBytes());
            proc.getOutputStream().flush();
        }
        long timeout = TIMEOUT;
        Object tf = flags.timeout;
        if (tf in Number) timeout=tf;
        else if (tf in TimeDuration) timeout=((TimeDuration)tf).toMilliseconds();
        if (tf!=null) timeout = tf;
        
        final AtomicBoolean ended = new AtomicBoolean(false);
        final AtomicBoolean killed = new AtomicBoolean(false);
        Thread t = new Thread({ try { 
            if (timeout>0) {
                sleep(timeout);
                if (!ended.get()) {
                        log.debug("Timeout exceeded for $context% ${cmd.join(" ")}");
                        proc.destroy();
                        killed.set(true);
                }
            } 
        } catch (Exception e) {} });
        if (TIMEOUT>0) t.start();
        int exitCode = proc.waitFor();
        ended.set(true);
        if (TIMEOUT>0) t.interrupt();
        
        stdoutG.blockUntilFinished();
        stderrG.blockUntilFinished();
        if (exitCode!=0 || killed.get()) {
            String message = killed.get() ? "terminated after timeout" : "exit code ${exitCode}";
            log.debug("Completed local command (problem, throwing): $context% ${cmd.join(" ")}; "+message);
            def e = "Command failed (${message}): "+cmd.join(" ");
            if (log) log.warn(e+"\n"+stdoutB+(stderrB.size()>0 ? "\n--\n"+stderrB : ""));
            throw new IllegalStateException(e+" (details logged)");
        }
        log.debug("Completed local command: $context% ${cmd.join(" ")}; exit code 0");
        return stdoutB.toString().split("\n");
    }

}
