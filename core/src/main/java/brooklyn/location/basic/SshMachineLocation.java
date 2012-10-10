package brooklyn.location.basic;

import static brooklyn.util.GroovyJavaMethods.truth;
import groovy.lang.Closure;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.net.InetAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jclouds.util.Throwables2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynLogging;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.OsDetails;
import brooklyn.location.PortRange;
import brooklyn.location.PortSupplier;
import brooklyn.location.basic.PortRanges.BasicPortRange;
import brooklyn.location.geo.HasHostGeoInfo;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.util.MutableMap;
import brooklyn.util.ReaderInputStream;
import brooklyn.util.ResourceUtils;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.internal.SshTool;
import brooklyn.util.internal.StreamGobbler;
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.internal.ssh.SshjTool;
import brooklyn.util.mutex.MutexSupport;
import brooklyn.util.mutex.WithMutexes;
import brooklyn.util.task.Tasks;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

/**
 * Operations on a machine that is accessible via ssh.
 * <p>
 * We expose two ways of running scripts.
 * One (execCommands) passes lines to bash, that is lightweight but fragile.
 * Another (execScript) creates a script on the remote machine, more portable but heavier.
 * <p>
 * Additionally there are routines to copyTo, copyFrom; and installTo (which tries a curl, and falls back to copyTo
 * in event the source is accessible by the caller only). 
 */
public class SshMachineLocation extends AbstractLocation implements MachineLocation, PortSupplier, WithMutexes {
    public static final Logger LOG = LoggerFactory.getLogger(SshMachineLocation.class);
    public static final Logger logSsh = LoggerFactory.getLogger(BrooklynLogging.SSH_IO);
    
    protected interface ExecRunner {
        public int exec(SshTool ssh, Map<String,?> flags, List<String> cmds, Map<String,?> env);
    }
    
    @SetFromFlag("user")
    String user;

    @SetFromFlag("privateKeyData")
    String privateKeyData;

    @SetFromFlag(nullable = false)
    InetAddress address;

    @SetFromFlag
    Map config;

    /** any property that should be passed as ssh config (connection-time) 
     *  can be prefixed with this and . and will be passed through (with the prefix removed),
     *  e.g. (SSHCONFIG_PREFIX+"."+"StrictHostKeyChecking"):"yes" */
    public static final String SSHCONFIG_PREFIX = "sshconfig";
    /** properties which are passed to ssh */
    public static final Collection<String> SSH_PROPS = ImmutableSet.of(
            "noStdoutLogging", "noStderrLogging", "logPrefix", "out", "err", "password", 
            "permissions", "sshTries", "env", "allocatePTY",
            "privateKeyPassphrase", "privateKeyFile", "privateKeyData", 
            // deprecated in 0.4.0 -- prefer privateKeyData/privateKeyFile 
            // (confusion about whether other holds a file or data; and public not useful here)
            // they generate a warning where used 
            "keyFiles", "publicKey", "privateKey");
    //TODO remove once everything is prefixed SSHCONFIG_PREFIX or included above
    public static final Collection<String> NON_SSH_PROPS = ImmutableSet.of("latitude", "longitude", "backup", "sshPublicKeyData", "sshPrivateKeyData");
    
    private final Set<Integer> ports = Sets.newLinkedHashSet();

    public SshMachineLocation() {
        this(MutableMap.of());
    }
    
    public SshMachineLocation(Map properties) {
        super(properties);
    }

    public void configure() {
        configure(MutableMap.of());
    }
    
    public void configure(Map properties) {
        if (config==null) config = Maps.newLinkedHashMap();

        super.configure(properties);

        if (properties.containsKey("username")) {
            LOG.warn("Using deprecated ssh machine property 'username': use 'user' instead", new Throwable("source of deprecated ssh 'username' invocation"));
            user = ""+properties.get("username");
        }
        Preconditions.checkNotNull(address, "address is required for SshMachineLocation");
        
        if (name == null) {
            name = (truth(user) ? user+"@" : "") + address.getHostName();
        }
        if (getHostGeoInfo() == null) {
            Location parentLocation = getParentLocation();
            if ((parentLocation instanceof HasHostGeoInfo) && ((HasHostGeoInfo)parentLocation).getHostGeoInfo()!=null)
                setHostGeoInfo( ((HasHostGeoInfo)parentLocation).getHostGeoInfo() );
            else
                setHostGeoInfo(HostGeoInfo.fromLocation(this));
        }
    }

    public InetAddress getAddress() {
        return address;
    }

    public String getUser() {
        return user;
    }
    
    public Map getConfig() {
        return config;
    }
    
    public int run(String command) {
        return run(MutableMap.of(), command, MutableMap.of());
    }
    public int run(Map props, String command) {
        return run(props, command, MutableMap.of());
    }
    public int run(String command, Map env) {
        return run(MutableMap.of(), command, env);
    }
    public int run(Map props, String command, Map env) {
        return run(props, ImmutableList.of(command), env);
    }

    /**
     * @deprecated in 1.4.1, @see execCommand and execScript
     */
    public int run(List<String> commands) {
        return run(MutableMap.of(), commands, MutableMap.of());
    }
    public int run(Map props, List<String> commands) {
        return run(props, commands, MutableMap.of());
    }
    public int run(List<String> commands, Map env) {
        return run(MutableMap.of(), commands, env);
    }
    public int run(Map props, List<String> commands, Map env) {
        Preconditions.checkNotNull(address, "host address must be specified for ssh");
        if (commands == null || commands.isEmpty()) return 0;
        SshTool ssh = connectSsh(props);
        try {
            return ssh.execShell(props, commands, env);
        } finally {
            ssh.disconnect();
        }
    }

    protected SshTool connectSsh() {
        return connectSsh(MutableMap.of());
    }
    
    protected boolean previouslyConnected = false;
    protected SshTool connectSsh(Map props) {
        try {
            if (!truth(user)) user = System.getProperty("user.name");
            Map<?,?> allprops = MutableMap.builder().putAll(config).putAll(leftoverProperties).putAll(props).build();
            Map<String,Object> args = MutableMap.<String,Object>of("user", user, "host", address.getHostName());
            for (Map.Entry<?, ?> entry : allprops.entrySet()) {
                String k = ""+entry.getKey();
                Object v = entry.getValue();
                if (SSH_PROPS.contains(k)) {
                    args.put(k, v);
                } else if (k.startsWith(SSHCONFIG_PREFIX+".")) {
                    args.put(k.substring(SSHCONFIG_PREFIX.length()+1), v);
                } else {
                    // TODO remove once everything is included above and we no longer see these warnings
                    if (!NON_SSH_PROPS.contains(k)) {
                        LOG.warn("including legacy SSH config property "+k+" for "+this+"; either prefix with sshconfig or add to NON_SSH_PROPS");
                        args.put(k, v);
                    }
                }
            }
            if (LOG.isTraceEnabled()) LOG.trace("creating ssh session for "+args);
            SshTool ssh = new SshjTool(args);
            Tasks.setBlockingDetails("Opening ssh connection");
            try { ssh.connect(); } finally { Tasks.setBlockingDetails(null); }
            previouslyConnected = true;
            return ssh;
        } catch (Exception e) {
            if (previouslyConnected) throw Throwables.propagate(e);
            // subsequence connection (above) most likely network failure, our remarks below won't help
            // on first connection include additional information if we can't connect, to help with debugging
            String rootCause = Throwables.getRootCause(e).getMessage();
            throw new IllegalStateException("Cannot establish ssh connection to "+user+" @ "+this+
                    (rootCause!=null && !rootCause.isEmpty() ? " ("+rootCause+")" : "")+". \n"+
                    "Ensure that passwordless and passphraseless ssh access is enabled using standard keys from ~/.ssh or " +
                    "as configured in brooklyn.properties. " +
                    "Check that the target host is accessible, that correct permissions are set on your keys, " +
                    "and that there is sufficient random noise in /dev/random on both ends. " +
                    "To debug less common causes, see the original error in the trace or log, and/or enable 'net.schmizz' (sshj) logging."
                    , e);
        }
    }

    /**
     * Convenience for running commands using ssh {@literal exec} mode.
     * @deprecated in 1.4.1, @see execCommand and execScript
     */
    public int exec(List<String> commands) {
        return exec(MutableMap.of(), commands, MutableMap.of());
    }
    public int exec(Map props, List<String> commands) {
        return exec(props, commands, MutableMap.of());
    }
    public int exec(List<String> commands, Map env) {
        return exec(MutableMap.of(), commands, env);
    }
    public int exec(Map props, List<String> commands, Map env) {
        Preconditions.checkNotNull(address, "host address must be specified for ssh");
        if (commands == null || commands.isEmpty()) return 0;
        SshTool ssh = connectSsh(props);
        int result = ssh.execCommands(props, commands, env);
        ssh.disconnect();
        return result;
    }
        
    // TODO submitCommands and submitScript which submit objects we can subsequently poll (cf JcloudsSshMachineLocation.submitRunScript)
    
    /** executes a set of commands, directly on the target machine (no wrapping in script).
     * joined using ' ; ' by default.
     * <p>
     * Stdout and stderr will be logged automatically to brooklyn.SSH logger, unless the 
     * flags 'noStdoutLogging' and 'noStderrLogging' are set. To set a logging prefix, use
     * the flag 'logPrefix'.
     * <p>
     * Currently runs the commands in an interactive/login shell
     * by passing each as a line to bash. To terminate early, use:
     * <pre>
     * foo || exit 1
     * </pre>
     * It may be desirable instead, in some situations, to wrap as:
     * <pre>
     * { line1 ; } && { line2 ; } ... 
     * </pre>
     * and run as a single command (possibly not as an interacitve/login
     * shell) causing the script to exit on the first command which fails.
     * <p>
     * Currently this has to be done by the caller.
     * (If desired we can add a flag {@code exitIfAnyNonZero} to support this mode,
     * and/or {@code commandPrepend} and {@code commandAppend} similar to 
     * (currently supported in SshjTool) {@code separator}.) 
     */
    public int execCommands(String summaryForLogging, List<String> commands) {
        return execCommands(MutableMap.<String,Object>of(), summaryForLogging, commands, MutableMap.<String,Object>of());
    }
    public int execCommands(Map<String,?> props, String summaryForLogging, List<String> commands) {
        return execCommands(props, summaryForLogging, commands, MutableMap.<String,Object>of());
    }
    public int execCommands(String summaryForLogging, List<String> commands, Map<String,?> env) {
        return execCommands(MutableMap.<String,Object>of(), summaryForLogging, commands, env);
    }
    public int execCommands(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
        return execWithLogging(props, summaryForLogging, commands, env, new ExecRunner() {
                @Override public int exec(SshTool ssh, Map<String,?> flags, List<String> cmds, Map<String,?> env) {
                    return ssh.execCommands(flags, cmds, env);
                }});
    }

    /** executes a set of commands, wrapped as a script sent to the remote machine.
     * <p>
     * Stdout and stderr will be logged automatically to brooklyn.SSH logger, unless the 
     * flags 'noStdoutLogging' and 'noStderrLogging' are set. To set a logging prefix, use
     * the flag 'logPrefix'.
     */
    public int execScript(String summaryForLogging, List<String> commands) {
        return execScript(MutableMap.<String,Object>of(), summaryForLogging, commands, MutableMap.<String,Object>of());
    }
    public int execScript(Map<String,?> props, String summaryForLogging, List<String> commands) {
        return execScript(props, summaryForLogging, commands, MutableMap.<String,Object>of());
    }
    public int execScript(String summaryForLogging, List<String> commands, Map<String,?> env) {
        return execScript(MutableMap.<String,Object>of(), summaryForLogging, commands, env);
    }
    public int execScript(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
        return execWithLogging(props, summaryForLogging, commands, env, new ExecRunner() {
                @Override public int exec(SshTool ssh, Map<String, ?> flags, List<String> cmds, Map<String, ?> env) {
                    return ssh.execScript(flags, cmds, env);
                }});
    }

    protected int execWithLogging(Map<String,?> props, String summaryForLogging, List<String> commands, Map env, final Closure<Integer> execCommand) {
        return execWithLogging(props, summaryForLogging, commands, env, new ExecRunner() {
                @Override public int exec(SshTool ssh, Map<String, ?> flags, List<String> cmds, Map<String, ?> env) {
                    return execCommand.call(ssh, flags, cmds, env);
                }});
    }
    
    protected int execWithLogging(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env, ExecRunner execCommand) {
        if (logSsh.isDebugEnabled()) logSsh.debug("{} on machine {}: {}", new Object[] {summaryForLogging, this, commands});
        
        Preconditions.checkNotNull(address, "host address must be specified for ssh");
        if (commands.isEmpty()) {
            logSsh.debug("{} on machine {} ending: no commands to run", summaryForLogging, this);
            return 0;
        }
        Map<String,Object> flags = MutableMap.copyOf(props);
        
        PipedOutputStream outO = null;
        PipedOutputStream outE = null;
        StreamGobbler gO=null, gE=null;
        try {
        	String logPrefix;
        	if (flags.get("logPrefix") != null) {
        		logPrefix = ""+flags.get("logPrefix"); 
        	} else {
        		String hostname = getAddress().getHostName();
        		Object port = config.get("sshconfig.port");
        		if (port == null) port = leftoverProperties.get("sshconfig.port");
        		logPrefix = (user != null ? user+"@" : "") + hostname + (port != null ? ":"+port : "");
        	}
        	
            if (!truth(flags.get("noStdoutLogging"))) {
                PipedInputStream insO = new PipedInputStream();
                outO = new PipedOutputStream(insO);
            
                String stdoutLogPrefix = "["+(logPrefix != null ? logPrefix+":stdout" : "stdout")+"] ";
                gO = new StreamGobbler(insO, (OutputStream) flags.get("out"), logSsh).setLogPrefix(stdoutLogPrefix);
                gO.start();
                
                flags.put("out", outO);
            }
            
            if (!truth(flags.get("noStdoutLogging"))) {
                PipedInputStream insE = new PipedInputStream();
                outE = new PipedOutputStream(insE);
            
                String stderrLogPrefix = "["+(logPrefix != null ? logPrefix+":stderr" : "stderr")+"] ";
                gE = new StreamGobbler(insE, (OutputStream) flags.get("err"), logSsh).setLogPrefix(stderrLogPrefix);
                gE.start();
                
                flags.put("err", outE);
            }
            
            SshTool ssh = connectSsh(flags);
            Tasks.setBlockingDetails("SSH executing, "+summaryForLogging);
            try {  
                int result = execCommand.exec(ssh, flags, commands, env);
                ssh.disconnect();
                if (logSsh.isDebugEnabled()) logSsh.debug("{} on machine {} completed: return status {}", new Object[] {summaryForLogging, this, result});

                return result;
            } finally { Tasks.setBlockingDetails(null); }
        } catch (IOException e) {
            if (logSsh.isDebugEnabled()) logSsh.debug("{} on machine {} failed: {}", new Object[] {summaryForLogging, this, e});
            throw Throwables.propagate(e);
        } finally {
            // Must close the pipedOutStreams, otherwise input will never read -1 so StreamGobbler thread would never die
            if (outO!=null) try { outO.flush(); } catch (IOException e) {}
            if (outE!=null) try { outE.flush(); } catch (IOException e) {}
            Closeables.closeQuietly(outO);
            Closeables.closeQuietly(outE);
            
            try {
                if (gE!=null) { gE.join(); }
                if (gO!=null) { gO.join(); }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Throwables.propagate(e);
            }
        }

    }

    public int copyTo(File src, File destination) {
        return copyTo(MutableMap.<String,Object>of(), src, destination);
    }
    public int copyTo(Map<String,?> props, File src, File destination) {
        return copyTo(props, src, destination.getPath());
    }

    // FIXME the return code is not a reliable indicator of success or failure
    public int copyTo(File src, String destination) {
        return copyTo(MutableMap.<String,Object>of(), src, destination);
    }
    public int copyTo(Map<String,?> props, File src, String destination) {
        Preconditions.checkNotNull(address, "Host address must be specified for scp");
        Preconditions.checkArgument(src.exists(), "File %s must exist for scp", src.getPath());
        try {
            return copyTo(props, new FileInputStream(src), src.length(), destination);
        } catch (FileNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }
    public int copyTo(Reader src, String destination) {
        return copyTo(MutableMap.<String,Object>of(), src, destination);
    }
	public int copyTo(Map<String,?> props, Reader src, String destination) {
		return copyTo(props, new ReaderInputStream(src), destination);
	}
    public int copyTo(InputStream src, String destination) {
        return copyTo(MutableMap.<String,Object>of(), src, destination);
    }
	public int copyTo(Map<String,?> props, InputStream src, String destination) {
		return copyTo(props, src, -1, destination);
	}
    public int copyTo(InputStream src, long filesize, String destination) {
        return copyTo(MutableMap.<String,Object>of(), src, filesize, destination);
    }
	public int copyTo(Map<String,?> props, InputStream src, long filesize, String destination) {
		if (filesize==-1) {
		    try {
		        byte[] bytes = ByteStreams.toByteArray(src);
		        filesize = bytes.length;
		        src = new ByteArrayInputStream(bytes);
		    } catch (IOException e) {
		        throw Throwables.propagate(e);
		    } finally {
		        Closeables.closeQuietly(src);
		    }
		}
		
        SshTool ssh = connectSsh(props);
        int result = ssh.createFile(props, destination, src, filesize);
        ssh.disconnect();
        return result;
    }

    // FIXME the return code is not a reliable indicator of success or failure
    public int copyFrom(String remote, String local) {
        return copyFrom(MutableMap.<String,Object>of(), remote, local);
    }
    public int copyFrom(Map<String,?> props, String remote, String local) {
        Preconditions.checkNotNull(address, "host address must be specified for scp");
        SshTool ssh = connectSsh(props);
        int result = ssh.transferFileFrom(props, remote, local);
        ssh.disconnect();
        return result;
    }

    /** installs the given URL at the indicated destination.
     * attempts to curl the sourceUrl on the remote machine,
     * then if that fails, loads locally (from classpath or file) and transfers.
     * <p>
     * accepts either a path (terminated with /) or filename for the destination. 
     **/
    public int installTo(ResourceUtils loader, String url, String destination) {
        if (destination.endsWith("/")) {
            String destName = url;
            destName = destName.contains("?") ? destName.substring(0, destName.indexOf("?")) : destName;
            destName = destName.substring(destName.lastIndexOf('/')+1);
            destination = destination + destName;            
        }
        LOG.debug("installing {} to {} on {}, attempting remote curl", new Object[] {url, destination, this});

        try {
            PipedInputStream insO = new PipedInputStream(); OutputStream outO = new PipedOutputStream(insO);
            PipedInputStream insE = new PipedInputStream(); OutputStream outE = new PipedOutputStream(insE);
            new StreamGobbler(insO, null, LOG).setLogPrefix("[curl @ "+address+":stdout] ").start();
            new StreamGobbler(insE, null, LOG).setLogPrefix("[curl @ "+address+":stdout] ").start();
            int result = exec(MutableMap.of("out", outO, "err", outE),
                    ImmutableList.of("curl "+url+" -L --silent --insecure --show-error --fail --connect-timeout 60 --max-time 600 --retry 5 -o "+destination));
            
            if (result!=0 && loader!=null) {
                LOG.debug("installing {} to {} on {}, curl failed, attempting local fetch and copy", new Object[] {url, destination, this});
                result = copyTo(loader.getResourceFromUrl(url), destination);
            }
            if (result==0)
                LOG.debug("installing {} complete; {} on {}", new Object[] {url, destination, this});
            else
                LOG.warn("installing {} failed; {} on {}: {}", new Object[] {url, destination, this, result});
            return result;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
    
    @Override
    public String toString() {
        return "SshMachineLocation["+name+":"+address+"]";
    }

    /**
     * @see #obtainPort(PortRange)
     * @see BasicPortRange#ANY_HIGH_PORT
     */
    public boolean obtainSpecificPort(int portNumber) {
	    // TODO Does not yet check if the port really is free on this machine
        if (ports.contains(portNumber)) {
            return false;
        } else {
            ports.add(portNumber);
            return true;
        }
    }

    public int obtainPort(PortRange range) {
        for (int p: range)
            if (obtainSpecificPort(p)) return p;
         if (LOG.isDebugEnabled()) LOG.debug("unable to find port in {} on {}; returning -1", range, this);
         return -1;
    }

    public void releasePort(int portNumber) {
        ports.remove((Object) portNumber);
    }

    public boolean isSshable() {
        String cmd = "date";
        try {
            int result = run(cmd);
            if (result == 0) {
                return true;
            } else {
                if (LOG.isDebugEnabled()) LOG.debug("Not reachable: {}, executing `{}`, exit code {}", new Object[] {this, cmd, result});
                return false;
            }
        } catch (SshException e) {
            if (LOG.isDebugEnabled()) LOG.debug("Exception checking if "+this+" is reachable; assuming not", e);
            return false;
        } catch (IllegalStateException e) {
            if (LOG.isDebugEnabled()) LOG.debug("Exception checking if "+this+" is reachable; assuming not", e);
            return false;
        } catch (RuntimeException e) {
            if (Throwables2.getFirstThrowableOfType(e, IOException.class) != null) {
                if (LOG.isDebugEnabled()) LOG.debug("Exception checking if "+this+" is reachable; assuming not", e);
                return false;
            } else {
                throw e;
            }
        }
    }
    
    @Override
    public OsDetails getOsDetails() {
        // TODO ssh and find out what we need to know, or use jclouds...
        return BasicOsDetails.Factory.ANONYMOUS_LINUX;
    }

    protected WithMutexes newMutexSupport() { return new MutexSupport(); }
    
    WithMutexes mutexSupport = newMutexSupport();
    
    @Override
    public void acquireMutex(String mutexId, String description) throws InterruptedException {
        mutexSupport.acquireMutex(mutexId, description);
    }

    @Override
    public boolean tryAcquireMutex(String mutexId, String description) {
        return mutexSupport.tryAcquireMutex(mutexId, description);
    }

    @Override
    public void releaseMutex(String mutexId) {
        mutexSupport.releaseMutex(mutexId);
    }

    @Override
    public boolean hasMutex(String mutexId) {
        return mutexSupport.hasMutex(mutexId);
    }
}
