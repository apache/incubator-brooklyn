package brooklyn.util.internal.ssh.process;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.internal.ssh.ShellAbstractTool;
import brooklyn.util.internal.ssh.ShellTool;
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.stream.StreamGobbler;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

/** Implementation of {@link ShellTool} which runs locally. */
public class ProcessTool extends ShellAbstractTool implements ShellTool {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessTool.class);
    
    public ProcessTool() {
        this(null);
    }
    
    public ProcessTool(Map<String,?> flags) {
        super(getOptionalVal(flags, PROP_LOCAL_TEMP_DIR));
        if (flags!=null) {
            MutableMap<String, Object> flags2 = MutableMap.copyOf(flags);
            flags2.remove(PROP_LOCAL_TEMP_DIR.getName());
            if (!flags2.isEmpty())
                LOG.warn(""+this+" ignoring unsupported constructor flags: "+flags);
        }
    }

    @Override
    public int execScript(Map<String,?> props, List<String> commands, Map<String,?> env) {
        try {
            OutputStream out = getOptionalVal(props, PROP_OUT_STREAM);
            OutputStream err = getOptionalVal(props, PROP_ERR_STREAM);
            String scriptDir = getOptionalVal(props, PROP_SCRIPT_DIR);
            Boolean noExtraOutput = getOptionalVal(props, PROP_NO_EXTRA_OUTPUT);
            Boolean runAsRoot = getOptionalVal(props, PROP_RUN_AS_ROOT);
            String separator = getOptionalVal(props, PROP_SEPARATOR);
            
            String scriptPath = scriptDir+"/brooklyn-"+System.currentTimeMillis()+"-"+Identifiers.makeRandomId(8)+".sh";

            String scriptContents = toScript(props, commands, env);

            if (LOG.isTraceEnabled()) LOG.trace("Running shell process (process) as script:\n{}", scriptContents);
            File to = new File(scriptPath);
            Files.createParentDirs(to);
            Files.copy(ByteStreams.newInputStreamSupplier(scriptContents.getBytes()), to);

            List<String> cmds = buildRunScriptCommand(scriptPath, noExtraOutput, runAsRoot);
            cmds.add(0, "chmod +x "+scriptPath);
            return asInt(execProcesses(cmds, null, out, err, separator, this), -1);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public int execCommands(Map<String,?> props, List<String> commands, Map<String,?> env) {
        if (props.containsKey("blocks") && props.get("blocks") == Boolean.FALSE) {
            throw new IllegalArgumentException("Cannot exec non-blocking: command="+commands);
        }
        OutputStream out = getOptionalVal(props, PROP_OUT_STREAM);
        OutputStream err = getOptionalVal(props, PROP_ERR_STREAM);
        String separator = getOptionalVal(props, PROP_SEPARATOR);

        List<String> allcmds = toCommandSequence(commands, null);

        String singlecmd = Joiner.on(separator).join(allcmds);
        if (getOptionalVal(props, PROP_RUN_AS_ROOT)==Boolean.TRUE) {
            LOG.warn("Cannot run as root when executing as command; run as a script instead (will run as normal user): "+singlecmd);
        }
        if (LOG.isTraceEnabled()) LOG.trace("Running shell command (process): {}", singlecmd);
        
        return asInt(execProcesses(allcmds, env, out, err, separator, this), -1);
    }

    /** executes a set of commands by sending them as a single process to `bash -c` 
     * (single command argument of all the commands, joined with separator)
     * <p>
     * consequence of this is that you should not normally need to escape things oddly in your commands, 
     * type them just as you would into a bash shell (if you find exceptions please note them here!)
     */
    public static int execProcesses(List<String> cmds, Map<String,?> env, OutputStream out, OutputStream err, String separator, Object contextForLogging) {
        return execSingleProcess(Arrays.asList("bash", "-c", Strings.join(cmds, Preconditions.checkNotNull(separator, "separator"))), 
                env, out, err, contextForLogging);
    }
    
    /** executes a single process made up of the given command words (*not* bash escaped);
     * should be portable across OS's */
    public static int execSingleProcess(List<String> cmdWords, Map<String,?> env, OutputStream out, OutputStream err, Object contextForLogging) {
        StreamGobbler errgobbler = null;
        StreamGobbler outgobbler = null;
        
        ProcessBuilder pb = new ProcessBuilder(cmdWords);
        if (env!=null) {
            for (Map.Entry<String,?> kv: env.entrySet()) pb.environment().put(kv.getKey(), String.valueOf(kv.getValue())); 
        }

        try {
            Process p = pb.start();
            
            if (out != null) {
                InputStream outstream = p.getInputStream();
                outgobbler = new StreamGobbler(outstream, out, (Logger) null);
                outgobbler.start();
            }
            if (err != null) {
                InputStream errstream = p.getErrorStream();
                errgobbler = new StreamGobbler(errstream, err, (Logger) null);
                errgobbler.start();
            }
            
            int result = p.waitFor();
            
            if (outgobbler != null) outgobbler.blockUntilFinished();
            if (errgobbler != null) errgobbler.blockUntilFinished();
            
            if (result==255)
                // this is not definitive, but tests (and code?) expects throw exception if can't connect;
                // only return exit code when it is exit code from underlying process;
                // we have no way to distinguish 255 from ssh failure from 255 from the command run through ssh ...
                // but probably 255 is from CLI ssh
                throw new SshException("exit code 255 from CLI ssh; probably failed to connect");
            
            return result;
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        } finally {
            closeWhispering(outgobbler, contextForLogging, "execProcess");
            closeWhispering(errgobbler, contextForLogging, "execProcess");
        }
    }

}
