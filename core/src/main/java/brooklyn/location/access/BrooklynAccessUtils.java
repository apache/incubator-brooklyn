package brooklyn.location.access;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.SupportsPortForwarding;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.net.Cidr;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.task.ssh.SshTasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Strings;

import com.google.common.base.Supplier;
import com.google.common.net.HostAndPort;

public class BrooklynAccessUtils {

    private static final Logger log = LoggerFactory.getLogger(BrooklynAccessUtils.class);
    
    public static final ConfigKey<PortForwardManager> PORT_FORWARDING_MANAGER = new BasicConfigKey<PortForwardManager>(
            PortForwardManager.class, "brooklyn.portforwarding.manager", "A port-forwarding manager to use at an entity "
                + "or a location, where supported; note this should normally be a serializable client instance to prevent "
                + "the creation of multiple disconnected instances via config duplication");
    
    public static final ConfigKey<Cidr> MANAGEMENT_ACCESS_CIDR = new BasicConfigKey<Cidr>(
            Cidr.class, "brooklyn.portforwarding.management.cidr", "CIDR to enable by default for port-forwarding for management",
            null);  // TODO should be a list

    public static HostAndPort getBrooklynAccessibleAddress(Entity entity, int port) {
        String host;
        
        // look up port forwarding
        PortForwardManager pfw = entity.getConfig(PORT_FORWARDING_MANAGER);
        if (pfw!=null) {
            Collection<Location> ll = entity.getLocations();
            Maybe<SupportsPortForwarding> machine = Machines.findUniqueElement(ll, SupportsPortForwarding.class);
            if (machine.isPresent()) {
                synchronized (BrooklynAccessUtils.class) {
                    // TODO finer-grained synchronization
                    
                    HostAndPort hp = pfw.lookup((MachineLocation)machine.get(), port);
                    if (hp!=null) return hp;
                    
                    Location l = (Location) machine.get();
                    if (l instanceof SupportsPortForwarding) {
                        Cidr source = entity.getConfig(MANAGEMENT_ACCESS_CIDR);
                        if (source!=null) {
                            log.debug("BrooklynAccessUtils requesting new port-forwarding rule to access "+port+" on "+entity+" (at "+l+", enabled for "+source+")");
                            // TODO discuss, is this the best way to do it
                            // (will probably _create_ the port forwarding rule!)
                            hp = ((SupportsPortForwarding) l).getSocketEndpointFor(source, port);
                            if (hp!=null) return hp;
                        } else {
                            log.warn("No "+MANAGEMENT_ACCESS_CIDR.getName()+" configured for "+entity+", so cannot forward port "+port+" " +
                            		"even though "+PORT_FORWARDING_MANAGER.getName()+" was supplied");
                        }
                    }
                }
            }
        }
        
        host = entity.getAttribute(Attributes.HOSTNAME);
        if (host!=null) return HostAndPort.fromParts(host, port);
        
        throw new IllegalStateException("Cannot find way to access port "+port+" on "+entity+" from Brooklyn (no host.name)");
    }

    /** attempts to resolve hostnameTarget from origin
     * @return null if it definitively can't be resolved,  
     * best-effort IP address if possible, or blank if we could not run ssh or make sense of the output */
    public static String getResolvedAddress(Entity entity, SshMachineLocation origin, String hostnameTarget) {
        ProcessTaskWrapper<Integer> task = SshTasks.newSshExecTaskFactory(origin, "ping -c 1 -t 1 "+hostnameTarget)
            .summary("checking resolution of "+hostnameTarget).allowingNonZeroExitCode().newTask();
        DynamicTasks.queueIfPossible(task).orSubmitAndBlock(entity).asTask().blockUntilEnded();
        if (task.asTask().isError()) {
            log.warn("ping could not be run, at "+entity+" / "+origin+": "+Tasks.getError(task.asTask()));
            return "";
        }
        if (task.getExitCode()==null || task.getExitCode()!=0) {
            if (task.getExitCode()!=null && task.getExitCode()<10) {
                // small number means ping failed to resolve or ping the hostname
                log.debug("not able to resolve "+hostnameTarget+" from "+origin+" for "+entity+" because exit code was "+task.getExitCode());
                return null;
            }
            // large number means ping probably did not run
            log.warn("ping not run as expected, at "+entity+" / "+origin+" (code "+task.getExitCode()+"):\n"+task.getStdout().trim()+" --- "+task.getStderr().trim());
            return "";
        }
        String out = task.getStdout();
        try {
            String line1 = Strings.getFirstLine(out);
            String ip = Strings.getFragmentBetween(line1, "(", ")");
            if (Strings.isNonBlank(ip)) 
                return ip;
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            /* ignore non-parseable output */ 
        }
        if (out.contains("127.0.0.1")) return "127.0.0.1";
        return "";
    }

    public static Supplier<String> resolvedAddressSupplier(final Entity entity, final SshMachineLocation origin, final String hostnameTarget) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return getResolvedAddress(entity, origin, hostnameTarget);
            }
        };
    }

}
