package brooklyn.util

import groovy.io.GroovyPrintStream

import java.io.File

import org.slf4j.Logger

import brooklyn.util.internal.StreamGobbler

class ShellUtils {

    public static TIMEOUT = 60*1000;
    
    /** as {@link #exec(String[], String[], File, String, Logger)} but uses `bash -l -c ${cmd}' to have
     * a good path, and defaults for other fields; requires a logger 
     * and a context object (whose toString is used in the logger and in error messages);
     * optionally takes a string to use as input to the command
     */
    public static String[] exec(String cmd, String input=null, Logger log, Object context) {
        exec(["bash", "-l", "-c", cmd] as String[], null, null, input, log, context);
    }
    
    /** executes the single given command (words) with given environmnet (inherited if null)
     * and cwd (. if null), feeding it the given input stream (if not null).
     * logs I/O at debug (if not null).
     * throws exception if return code non-zero, otherwise returns lines from stdout.
     */
    public static String[] exec(String[] cmd, String[] envp, File dir, String input, Logger log, Object context) {
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
        Thread t = new Thread({ try { sleep(TIMEOUT); proc.destroy(); } catch (Exception e) {} });
        if (TIMEOUT>0) t.start();
        int exitCode = proc.waitFor();
        if (TIMEOUT>0) t.interrupt();
        stdoutG.blockUntilFinished();
        stderrG.blockUntilFinished();
        if (exitCode!=0) {
            def e = "Command failed (exit code ${exitCode}: "+cmd.join(" ");
            if (log) log.warn(e+"\n"+stdoutB+(stderrB.size()>0 ? "\n--\n"+stderrB : ""));
            throw new IllegalStateException(e+" (details logged)");
        }
        return stdoutB.toString().split("\n");
    }

}
