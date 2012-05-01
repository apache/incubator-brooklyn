package brooklyn.location.basic

import java.util.List;
import java.util.Map;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.location.MachineLocation
import brooklyn.location.OsDetails
import brooklyn.location.PortRange
import brooklyn.location.PortSupplier
import brooklyn.location.geo.HasHostGeoInfo
import brooklyn.location.geo.HostGeoInfo
import brooklyn.util.ReaderInputStream
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.mutex.MutexSupport
import brooklyn.util.mutex.WithMutexes
import brooklyn.util.internal.SshTool
import brooklyn.util.internal.ssh.SshException;
import brooklyn.util.internal.ssh.SshjTool

import com.google.common.base.Preconditions

/**
 * Operations on a machine that is accessible via ssh.
 */
public class SshMachineLocation extends AbstractLocation implements MachineLocation, PortSupplier, WithMutexes {
    public static final Logger LOG = LoggerFactory.getLogger(SshMachineLocation.class)
            
    @SetFromFlag('username')
    String user

    @SetFromFlag(nullable = false)
    InetAddress address

    @SetFromFlag
    Map config

    /** any property that should be passed as ssh config (connection-time) 
     *  can be prefixed with this and . and will be passed through (with the prefix removed),
     *  e.g. (SSHCONFIG_PREFIX+"."+"StrictHostKeyChecking"):"yes" */
    public static final String SSHCONFIG_PREFIX = "sshconfig";
    //TODO remove once everything is prefixed SSHCONFIG_PREFIX
    //(I don't think we ever relied on props being passed through in this way,
    //but the code path was there so I didn't want to delete it immediately.)
    public static final String NON_SSH_PROPS = ["out", "err", "latitude", "longitude", "keyFiles", "publicKey", "privateKey"];
    
    private final Set<Integer> ports = [] as HashSet

    public SshMachineLocation(Map properties=[:]) {
        super(properties)
    }
    
    public void configure(Map properties=[:]) {
        if (config==null) config = [:]

        super.configure(properties)

        Preconditions.checkNotNull(address, "address is required for SshMachineLocation")
        String host = (user ? "${user}@" : "") + address.hostName
        
        if (name == null) {
            name = host
        }
        if (getHostGeoInfo()==null) {
            if ((parentLocation instanceof HasHostGeoInfo) && ((HasHostGeoInfo)parentLocation).getHostGeoInfo()!=null)
                setHostGeoInfo( ((HasHostGeoInfo)parentLocation).getHostGeoInfo() );
            else
                setHostGeoInfo(HostGeoInfo.fromLocation(this));
        }
    }

    public InetAddress getAddress() { return address }

    public int run(Map props=[:], String command, Map env=[:]) {
        run(props, [ command ], env)
    }

    /**
     * Convenience for running a script, returning the result code.
     *
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
     *
     * @todo Perhaps add a flag {@code exitIfAnyNonZero} to toggle between
     *       the above modes ?
     */
    public int run(Map props=[:], List<String> commands, Map env=[:]) {
        Preconditions.checkNotNull address, "host address must be specified for ssh"
        if (!commands) return 0
        SshTool ssh = connectSsh(props)
        int result = ssh.execShell props, commands, env
        ssh.disconnect()
        result
    }
    
    protected SshTool connectSsh(Map props=[:]) {
        if (!user) user = System.getProperty "user.name"
        Map args = [ user:user, host:address.hostName ]
        (props+config+leftoverProperties).each { kk,v ->
            String k = ""+kk;
            if (k.startsWith(SSHCONFIG_PREFIX+".")) {
                args.put(k.substring(SSHCONFIG_PREFIX.length()+1), v);
            } else {
                // TODO remove once everything is prefixed SSHCONFIG_PREFIX
                if (!NON_SSH_PROPS.contains(k)) {
                    LOG.warn("including legacy SSH config property "+k+" for "+this+"; either prefix with sshconfig or add to NON_SSH_PROPS");
                }
                args.put(k, v);
            }
        }
        if (LOG.isTraceEnabled()) LOG.trace("creating ssh session for "+args);
        SshTool ssh = new SshjTool(args)
        ssh.connect()
        return ssh;
    }

    /**
     * Convenience for running commands using ssh {@literal exec} mode.
     */
    public int exec(Map props=[:], List<String> commands, Map env=[:]) {
        Preconditions.checkNotNull address, "host address must be specified for ssh"
        if (!commands) return 0
        SshjTool ssh = connectSsh(props)
        int result = ssh.execCommands props, commands, env
        ssh.disconnect()
        result
    }

    public int copyTo(Map props=[:], File src, File destination) {
        return copyTo(props, src, destination.path)
    }

    // FIXME the return code is not a reliable indicator of success or failure
    public int copyTo(Map props=[:], File src, String destination) {
        Preconditions.checkNotNull address, "Host address must be specified for scp"
        Preconditions.checkArgument src.exists(), "File %s must exist for scp", src.path
		copyTo new FileInputStream(src), src.length(), destination 
    }
	public int copyTo(Map props=[:], Reader src, String destination) {
		copyTo(props, new ReaderInputStream(src), destination);
	}
	public int copyTo(Map props=[:], InputStream src, String destination) {
		copyTo(props, src, -1, destination)
	}
	public int copyTo(Map props=[:], InputStream src, long filesize, String destination) {
		if (filesize==-1) {
			def bytes = src.getBytes()
			filesize = bytes.size()
			src = new ByteArrayInputStream(bytes)
		}
		
        SshTool ssh = connectSsh(props)
        int result = ssh.createFile props, destination, src, filesize
        ssh.disconnect()
        result
    }

    // FIXME the return code is not a reliable indicator of success or failure
    public int copyFrom(Map props=[:], String remote, String local) {
        Preconditions.checkNotNull address, "host address must be specified for scp"
        SshTool ssh = connectSsh(props);
        int result = ssh.transferFileFrom props, remote, local
        ssh.disconnect()
        result
    }

    @Override
    public String toString() {
        return address
    }

    /**
     * @see #obtainPort(PortRange)
     * @see BasicPortRange#ANY_HIGH_PORT
     */
    public boolean obtainSpecificPort(int portNumber) {
	    // TODO Does not yet check if the port really is free on this machine
        if (ports.contains(portNumber)) {
            return false
        } else {
            ports.add(portNumber)
            return true
        }
    }

    public int obtainPort(PortRange range) {
        for (int p: range)
            if (obtainSpecificPort(p)) return p
         if (LOG.isDebugEnabled()) LOG.debug("unable to find port in {} on {}; returning -1", range, this)
         return -1
    }

    public void releasePort(int portNumber) {
        ports.remove((Object) portNumber)
    }

    public boolean isSshable() {
        String cmd = "date"
        try {
            int result = run(cmd)
            if (result == 0) {
                return true
            } else {
                if (LOG.isDebugEnabled()) LOG.debug("Not reachable: $this, executing `$cmd`, exit code $result")
                return false
            }
        } catch (SshException e) {
            if (LOG.isDebugEnabled()) LOG.debug("Exception checking if $this is reachable; assuming not", e)
            return false
        } catch (IllegalStateException e) {
            if (LOG.isDebugEnabled()) LOG.debug("Exception checking if $this is reachable; assuming not", e)
            return false
        } catch (IOException e) {
            if (LOG.isDebugEnabled()) LOG.debug("Exception checking if $this is reachable; assuming not", e)
            return false
        }
    }
    
    @Override
    public OsDetails getOsDetails() {
        return BasicOsDetails.Factory.ANONYMOUS_LINUX;
    }

    protected WithMutexes newMutexSupport() { new MutexSupport(); }
    
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
