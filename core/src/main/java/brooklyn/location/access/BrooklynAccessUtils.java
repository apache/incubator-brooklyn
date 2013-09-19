package brooklyn.location.access;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.common.net.HostAndPort;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.location.basic.SupportsPortForwarding;
import brooklyn.util.net.Cidr;

public class BrooklynAccessUtils {

    private static final Logger log = LoggerFactory.getLogger(BrooklynAccessUtils.class);
    
    public static final ConfigKey<PortForwardManager> PORT_FORWARDING_MANAGER = new BasicConfigKey<PortForwardManager>(
            PortForwardManager.class, "brooklyn.portforwarding.manager");
    
    public static final ConfigKey<Cidr> MANAGEMENT_ACCESS_CIDR = new BasicConfigKey<Cidr>(
            Cidr.class, "brooklyn.portforwarding.management.cidr", "CIDR to enable by default for port-forwarding for management",
            null);  // TODO should be a list

    public static HostAndPort getBrooklynAccessibleAddress(Entity entity, int port) {
        String host;
        
        // look up port forwarding
        PortForwardManager pfw = entity.getConfig(PORT_FORWARDING_MANAGER);
        if (pfw!=null) {
            Collection<Location> ll = entity.getLocations();
            if (ll.size()==1) {
                synchronized (BrooklynAccessUtils.class) {
                    // TODO finer-grained synchronization
                    
                    HostAndPort hp = pfw.lookup(Iterables.getOnlyElement(ll), port);
                    if (hp!=null) return hp;
                    
                    Location l = Iterables.getOnlyElement(ll);
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

}
