package brooklyn.util.internal.duplicates;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import brooklyn.event.feed.shell.ShellFeed;

/**
 * The ExecUtils
 *
 * @deprecated since 0.6; use {@link brooklyn.util.stream.StreamGobbler}, and see {@link ShellFeed} 
 * @author aled
 **/
@Deprecated
public class ExecUtils {

    // TODO Do we need to extract code from ShellFeed to make it reusable as direct calls,
    // rather than just for polling to populate an attribute? e.g. have ShellHelper?
    
    public static class StreamGobbler extends Thread {
        protected final InputStream stream;
        protected final PrintStream out;
        public StreamGobbler(InputStream stream, PrintStream out) {
            this.stream = stream;
            this.out = out;
        }
        public void run() {
            int c = -1;
            try {
                while ((c=stream.read())>=0) {
                    onChar(c);
                }
            } catch (IOException e) {
                System.err.println("ERROR reading stream "+stream+": "+e);
            }
            onClose();
        }
        public void onChar(int c) {
            out.print((char)c);
        }
        public void onClose() {}
    }
    
    public static int execBlocking(String... cmd) throws IOException {
        Process p = Runtime.getRuntime().exec(cmd);
        new StreamGobbler(p.getInputStream(), System.out).start();
        new StreamGobbler(p.getErrorStream(), System.err).start();
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        return p.exitValue();
    }

    
//    public static int execBlockingAndLog(Logger log, Level level, String... cmd) throws IOException {
//        return execBlockingAndLog("", log, level, level, cmd);
//    }
//
//    public static int execBlockingAndLog(String logPrefix, Logger log, Level stdoutLevel, Level stderrLevel, String... cmd) throws IOException {
//        log.log(stdoutLevel, "Executing "+StringUtils.join(cmd," "));
//        Process p = Runtime.getRuntime().exec(cmd);
//        new StreamGobblerToLogger(p.getInputStream(), log, stdoutLevel, logPrefix+" stdout: ").run();
//        new StreamGobblerToLogger(p.getErrorStream(), log, stderrLevel, logPrefix+" stderr: ").run();
//        try {
//            p.waitFor();
//        } catch (InterruptedException e) {
//            throw ExceptionUtils.throwRuntime(e);
//        }
//        int exitStatus = p.exitValue();
//        
//        log.log((exitStatus == 0 ? stdoutLevel : Level.WARNING), MessageFormat.format(
//                "Executed ''{2}'' returned {0} ({1})",
//                exitStatus, (exitStatus == 0 ? "success" : "failure"), StringUtils.join(cmd," ")));
//        
//        return exitStatus;
//    }
}
