package brooklyn.entity.dns;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.util.flags.SetFromFlag;

public interface AbstractGeoDnsService extends Entity {
    @SetFromFlag("pollPeriod")
    public static final ConfigKey<Long> POLL_PERIOD = new BasicConfigKey<Long>(Long.class, "geodns.pollperiod", "Poll period (in milliseconds) for refreshing target hosts", 5000L);
    public static final ConfigKey<Boolean> INCLUDE_HOMELESS_ENTITIES = ConfigKeys.newBooleanConfigKey("geodns.includeHomeless", "Whether to include entities whose geo-coordinates cannot be inferred", false);
    public static final ConfigKey<Boolean> USE_HOSTNAMES = ConfigKeys.newBooleanConfigKey("geodns.useHostnames", "Whether to use the hostname for the returned value, for routing (rather than IP address)", true);
    
    public static final AttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;
    public static final AttributeSensor<Boolean> SERVICE_UP = Startable.SERVICE_UP;
    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    public static final AttributeSensor<Map<String,String>> TARGETS = new BasicAttributeSensor(
            Map.class, "geodns.targets", "Map of targets currently being managed (entity ID to URL)");

    public void setServiceState(Lifecycle state);
    
    /** if target is a group, its members are searched; otherwise its children are searched */
    public void setTargetEntityProvider(final Entity entityProvider);
    
    /** should return the hostname which this DNS service is configuring */
    public String getHostname();
    
    public Map<Entity, HostGeoInfo> getTargetHosts();
}
