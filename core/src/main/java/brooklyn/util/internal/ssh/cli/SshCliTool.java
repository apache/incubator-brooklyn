package brooklyn.util.internal.ssh.cli;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.BasicConfigKey.StringConfigKey;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.internal.ssh.SshAbstractTool;
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.ssh.CommonCommands;
import brooklyn.util.stream.StreamGobbler;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * For ssh and scp commands, delegating to system calls.
 */
public class SshCliTool extends SshAbstractTool implements SshTool {

    // TODO No retry support, with backoffLimitedRetryHandler
    
    private static final Logger LOG = LoggerFactory.getLogger(SshCliTool.class);

    public static final ConfigKey<String> PROP_SSH_EXECUTABLE = new StringConfigKey("sshExecutable", "command to execute for ssh (defaults to \"ssh\", but could be overridden to sshg3 for Tectia for example)", "ssh");
    public static final ConfigKey<String> PROP_SSH_FLAGS = new StringConfigKey("sshFlags", "flags to pass to ssh, as a space separated list", "");
    public static final ConfigKey<String> PROP_SCP_EXECUTABLE = new StringConfigKey("scpExecutable", "command to execute for scp (defaults to \"scp\", but could be overridden to scpg3 for Tectia for example)", "scp");

    public static Builder<SshCliTool,?> builder() {
        return new ConcreteBuilder();
    }
    
    private static class ConcreteBuilder extends Builder<SshCliTool, ConcreteBuilder> {
    }
    
    public static class Builder<T extends SshCliTool, B extends Builder<T,B>> extends AbstractToolBuilder<T,B> {
        private String sshExecutable;
        private String sshFlags;
        private String scpExecutable;

        @Override
        public B from(Map<String,?> props) {
            super.from(props);
            sshExecutable = getOptionalVal(props, PROP_SSH_EXECUTABLE);
            sshFlags = getOptionalVal(props, PROP_SSH_FLAGS);
            scpExecutable = getOptionalVal(props, PROP_SCP_EXECUTABLE);
            return self();
        }
        public B sshExecutable(String val) {
            this.sshExecutable = val; return self();
        }
        public B scpExecutable(String val) {
            this.scpExecutable = val; return self();
        }
        @SuppressWarnings("unchecked")
        public T build() {
            return (T) new SshCliTool(this);
        }
    }

    private final String sshExecutable;
    private final String sshFlags;
    private final String scpExecutable;

    public SshCliTool(Map<String,?> map) {
        this(builder().from(map));
    }
    
    private SshCliTool(Builder<?,?> builder) {
        super(builder);
        sshExecutable = checkNotNull(builder.sshExecutable);
        sshFlags = checkNotNull(builder.sshFlags);
        scpExecutable = checkNotNull(builder.scpExecutable);
        if (LOG.isTraceEnabled()) LOG.trace("Created SshCliTool {} ({})", this, System.identityHashCode(this));
    }
    
    @Override
    public void connect() {
        // no-op
    }

    @Override
    public void connect(int maxAttempts) {
        // no-op
    }

    @Override
    public void disconnect() {
        if (LOG.isTraceEnabled()) LOG.trace("Disconnecting SshCliTool {} ({}) - no-op", this, System.identityHashCode(this));
        // no-op
    }

    @Override
    public boolean isConnected() {
        // TODO Always pretends to be connected
        return true;
    }

    @Override
    public int copyToServer(java.util.Map<String,?> props, byte[] contents, String pathAndFileOnRemoteServer) {
        return copyTempFileToServer(props, writeTempFile(contents), pathAndFileOnRemoteServer);
    }
    
    @Override
    public int copyToServer(java.util.Map<String,?> props, InputStream contents, String pathAndFileOnRemoteServer) {
        return copyTempFileToServer(props, writeTempFile(contents), pathAndFileOnRemoteServer);
    }
    
    @Override
    public int copyToServer(Map<String,?> props, File f, String pathAndFileOnRemoteServer) {
        if (hasVal(props, PROP_LAST_MODIFICATION_DATE)) {
            LOG.warn("Unsupported ssh feature, setting lastModificationDate for {}:{}", this, pathAndFileOnRemoteServer);
        }
        if (hasVal(props, PROP_LAST_ACCESS_DATE)) {
            LOG.warn("Unsupported ssh feature, setting lastAccessDate for {}:{}", this, pathAndFileOnRemoteServer);
        }
        String permissions = getOptionalVal(props, PROP_PERMISSIONS);
        
        int result = scpToServer(props, f, pathAndFileOnRemoteServer);
        if (result == 0) {
            result = chmodOnServer(props, permissions, pathAndFileOnRemoteServer);
            if (result != 0) {
                LOG.warn("Error setting file permissions to {}, after copying file {} to {}:{}; exit code {}", new Object[] {permissions, pathAndFileOnRemoteServer, this, f, result});
            }
        } else {
            LOG.warn("Error copying file {} to {}:{}; exit code {}", new Object[] {pathAndFileOnRemoteServer, this, f, result});
        }
        return result;
    }

    private int copyTempFileToServer(Map<String,?> props, File f, String pathAndFileOnRemoteServer) {
        try {
            return copyToServer(props, f, pathAndFileOnRemoteServer);
        } finally {
            f.delete();
        }
    }

    @Override
    public int copyFromServer(Map<String,?> props, String pathAndFileOnRemoteServer, File localFile) {
        return scpFromServer(props, pathAndFileOnRemoteServer, localFile);
    }

    @Override
    public int execScript(Map<String,?> props, List<String> commands) {
        return execScript(props, commands, Collections.<String,Object>emptyMap());
    }
    
    @Override
    public int execScript(Map<String,?> props, List<String> commands, Map<String,?> env) {
        String separator = getOptionalVal(props, PROP_SEPARATOR);
        String scriptDir = getOptionalVal(props, PROP_SCRIPT_DIR);
        Boolean runAsRoot = getOptionalVal(props, PROP_RUN_AS_ROOT);
        String scriptPath = scriptDir+"/brooklyn-"+System.currentTimeMillis()+"-"+Identifiers.makeRandomId(8)+".sh";

        String scriptContents = toScript(props, commands, env);
        
        if (LOG.isTraceEnabled()) LOG.trace("Running shell command at {} as script: {}", host, scriptContents);
        
        copyTempFileToServer(ImmutableMap.of("permissions", "0700"), writeTempFile(scriptContents), scriptPath);
        
        // use "-f" because some systems have "rm" aliased to "rm -i"; use "< /dev/null" to guarantee doesn't hang
        String cmd = 
                (runAsRoot ? CommonCommands.sudo(scriptPath) : scriptPath) + " < /dev/null"+separator+
                "RESULT=$?"+separator+
                "echo Executed "+scriptPath+", result $RESULT"+separator+ 
                "rm -f "+scriptPath+" < /dev/null"+separator+
                "exit $RESULT";
        Integer result = sshExec(props, cmd);
        return result != null ? result : -1;
    }

    @Override
    public int execCommands(Map<String,?> props, List<String> commands) {
        return execCommands(props, commands, Collections.<String,Object>emptyMap());
    }

    @Override
    public int execCommands(Map<String,?> props, List<String> commands, Map<String,?> env) {
        return execScript(props, commands, env);
    }
    
    private int scpToServer(Map<String,?> props, File local, String remote) {
        String to = (Strings.isEmpty(getUsername()) ? "" : getUsername()+"@")+getHostAddress()+":"+remote;
        return scpExec(props, local.getAbsolutePath(), to);
    }

    private int scpFromServer(Map<String,?> props, String remote, File local) {
        String from = (Strings.isEmpty(getUsername()) ? "" : getUsername()+"@")+getHostAddress()+":"+remote;
        return scpExec(props, from, local.getAbsolutePath());
    }
    
    private int chmodOnServer(Map<String,?> props, String permissions, String remote) {
        return sshExec(props, "chmod "+permissions+" "+remote);
    }

    private int scpExec(Map<String,?> props, String from, String to) {
        File tempFile = null;
        try {
            List<String> cmd = Lists.newArrayList();
            cmd.add(getOptionalVal(props, PROP_SCP_EXECUTABLE, scpExecutable));
            if (privateKeyFile != null) {
                cmd.add("-i");
                cmd.add(privateKeyFile.getAbsolutePath());
            } else if (privateKeyData != null) {
                tempFile = writeTempFile(privateKeyData);
                cmd.add("-i");
                cmd.add(tempFile.getAbsolutePath());
            }
            if (!strictHostKeyChecking) {
                cmd.add("-o");
                cmd.add("StrictHostKeyChecking=no");
            }
            if (port != 22) {
                cmd.add("-P");
                cmd.add(""+port);
            }
            cmd.add(from);
            cmd.add(to);
            
            if (LOG.isTraceEnabled()) LOG.trace("Executing with command: {}", cmd);
            int result = execProcess(props, cmd);
            
            if (LOG.isTraceEnabled()) LOG.trace("Executed command: {}; exit code {}", cmd, result);
            return result;

        } finally {
            if (tempFile != null) tempFile.delete();
        }
    }
    
    private int sshExec(Map<String,?> props, String command) {
        File tempCmdFile = writeTempFile(command);
        tempCmdFile.setExecutable(true);
        File tempKeyFile = null;
        try {
            List<String> cmd = Lists.newArrayList();
            cmd.add(getOptionalVal(props, PROP_SSH_EXECUTABLE, sshExecutable));
            String propsFlags = getOptionalVal(props, PROP_SSH_FLAGS, sshFlags);
            if (propsFlags!=null && propsFlags.trim().length()>0)
                cmd.addAll(Arrays.asList(propsFlags.trim().split(" ")));
            if (privateKeyFile != null) {
                cmd.add("-i");
                cmd.add(privateKeyFile.getAbsolutePath());
            } else if (privateKeyData != null) {
                tempKeyFile = writeTempFile(privateKeyData);
                cmd.add("-i");
                cmd.add(tempKeyFile.getAbsolutePath());
            }
            if (!strictHostKeyChecking) {
                cmd.add("-o");
                cmd.add("StrictHostKeyChecking=no");
            }
            if (port != 22) {
                cmd.add("-P");
                cmd.add(""+port);
            }
            if (allocatePTY) {
                // have to be careful with double -tt as it can leave a shell session active
                // when done from bash (ie  ssh -tt localhost < /tmp/myscript.sh);
                // hover that doesn't seem to be a problem the way we use it from brooklyn
                // (and note single -t doesn't work _programmatically_ since the input isn't a terminal)
                cmd.add("-tt");
            }
            cmd.add((Strings.isEmpty(getUsername()) ? "" : getUsername()+"@")+getHostAddress());
            
            cmd.add(tempCmdFile.getAbsolutePath());
            // previously we tried these approaches:
            //cmd.add("$(<"+tempCmdFile.getAbsolutePath()+")");
            // only pays attention to the first word; the "; echo Executing ..." get treated as arguments
            // to the script in the first word, when invoked from java (when invoked from prompt the behaviour is as desired)
            //cmd.add("\""+command+"\"");
            // only works if command is a single word
            //cmd.add("bash -c \""+command+"\"");
            // this is a viable alternative, but seems safer to write to a script, esp since the code was already there
            
            if (LOG.isTraceEnabled()) LOG.trace("Executing ssh with command: {} (with {})", command, cmd);
            int result = execProcess(props, cmd);
            
            if (LOG.isTraceEnabled()) LOG.trace("Executed command: {}; exit code {}", cmd, result);
            return result;
            
        } finally {
            tempCmdFile.delete();
            if (tempKeyFile != null) tempKeyFile.delete();
        }
    }
    
    private int execProcess(Map<String,?> props, List<String> cmd) {
        OutputStream out = getOptionalVal(props, PROP_OUT_STREAM);
        OutputStream err = getOptionalVal(props, PROP_ERR_STREAM);
        StreamGobbler errgobbler = null;
        StreamGobbler outgobbler = null;
        
        ProcessBuilder pb = new ProcessBuilder(cmd);

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
            closeWhispering(outgobbler, this);
            closeWhispering(errgobbler, this);
        }
    }
    
}
