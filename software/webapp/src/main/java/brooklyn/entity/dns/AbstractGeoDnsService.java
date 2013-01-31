package brooklyn.entity.dns;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Startable;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.util.flags.SetFromFlag;

public interface AbstractGeoDnsService extends Entity {
    @SetFromFlag("pollPeriod")
    public static final ConfigKey<Long> POLL_PERIOD = new BasicConfigKey<Long>(Long.class, "geodns.pollperiod", "Poll period (in milliseconds) for refreshing target hosts", 5000L);
    public static final BasicAttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;
    public static final Sensor SERVICE_UP = Startable.SERVICE_UP;
    public static final BasicAttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    public static final BasicAttributeSensor<String> TARGETS = new BasicAttributeSensor<String>(
            String.class, "geodns.targets", "Map of targets currently being managed (entity ID to URL)");

    public void setServiceState(Lifecycle state);
    
    /** if target is a group, its members are searched; otherwise its children are searched */
    public void setTargetEntityProvider(final Entity entityProvider);
    
    /** should return the hostname which this DNS service is configuring */
    public String getHostname();
    
    public Map<Entity, HostGeoInfo> getTargetHosts();
}
