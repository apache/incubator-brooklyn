package brooklyn.event.basic;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.event.Sensor;
import brooklyn.location.Location;
import brooklyn.location.PortRange;
import brooklyn.location.PortSupplier;

/**
 * A {@link Sensor} describing a port on a system,
 * with a {@link ConfigKey} which can be configured with a port range
 * (either a number e.g. 80, or a string e.g. "80" or "8080-8089" or even "80, 8080-8089, 8800+", or a list of these).
 * <p>
 * To convert at runtime a single port is chosen, respecting the entity.
 */
public class PortAttributeSensorAndConfigKey extends AttributeSensorAndConfigKey<PortRange,Integer> {
    private static final long serialVersionUID = 4680651022807491321L;
    
    public static final Logger LOG = LoggerFactory.getLogger(PortAttributeSensorAndConfigKey.class);

    public PortAttributeSensorAndConfigKey(String name) {
        this(name, name, null);
    }
    public PortAttributeSensorAndConfigKey(String name, String description) {
        this(name, description, null);
    }
    public PortAttributeSensorAndConfigKey(String name, String description, Object defaultValue) {
        super(PortRange.class, Integer.class, name, description, defaultValue);
    }
    public PortAttributeSensorAndConfigKey(PortAttributeSensorAndConfigKey orig, Object defaultValue) {
        // TODO is this cast valid? could the port range be a string yet to be coerced for example?
        super(orig, (PortRange)defaultValue);
    }
    protected Integer convertConfigToSensor(PortRange value, Entity entity) {
        if (value==null) return null;
        Collection<Location> locations = entity.getLocations();
        if (!locations.isEmpty()) {
            if (locations.size()==1) {
                Location l = locations.iterator().next();
                if (l instanceof PortSupplier) {
                    int p = ((PortSupplier)l).obtainPort(value);
                    if (p!=-1) {
                        LOG.debug(""+entity+" choosing port "+p+" for "+getName());
                        return p;
                    }
                    LOG.warn(""+entity+" no port available for "+getName()+" in range "+value);
                    // definitively, no ports available
                    return null;
                }
                // ports may be available, we just can't tell from the location
                Integer v = (value.isEmpty() ? null : value.iterator().next());
                LOG.debug(""+entity+" choosing port "+v+" (unconfirmed) for "+getName());
                return v;
            }
        }
        LOG.info(""+entity+" ports not applicable, or not yet applicable, in location "+locations+", ignoring "+getName());
        return null;
    }
}
