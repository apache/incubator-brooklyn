package brooklyn.entity.dns;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.geo.HostGeoInfo;

import com.google.common.reflect.TypeToken;

public interface AbstractGeoDnsService extends Entity {
    
    public static final ConfigKey<Boolean> INCLUDE_HOMELESS_ENTITIES = ConfigKeys.newBooleanConfigKey("geodns.includeHomeless", "Whether to include entities whose geo-coordinates cannot be inferred", false);
    public static final ConfigKey<Boolean> USE_HOSTNAMES = ConfigKeys.newBooleanConfigKey("geodns.useHostnames", "Whether to use the hostname for the returned value for routing, rather than IP address (defaults to true)", true);
    
    public static final AttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;
    public static final AttributeSensor<Boolean> SERVICE_UP = Startable.SERVICE_UP;
    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    public static final AttributeSensor<String> ADDRESS = Attributes.ADDRESS;
    @SuppressWarnings("serial")
    public static final AttributeSensor<Map<String,String>> TARGETS = new BasicAttributeSensor<Map<String,String>>(
            new TypeToken<Map<String,String>>() {}, "geodns.targets", "Map of targets currently being managed (entity ID to URL)");

    public void setServiceState(Lifecycle state);
    
    /** sets target to be a group whose *members* will be searched (non-Group items not supported) */
    // prior to 0.7.0 the API accepted non-group items, but did not handle them correctly
    public void setTargetEntityProvider(final Group entityProvider);
    
    /** should return the hostname which this DNS service is configuring */
    public String getHostname();
    
    public Map<Entity, HostGeoInfo> getTargetHosts();
}
