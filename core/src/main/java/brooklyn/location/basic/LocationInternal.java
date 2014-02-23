package brooklyn.location.basic;

import java.util.Map;

import com.google.common.annotations.Beta;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.entity.rebind.Rebindable;
import brooklyn.location.Location;
import brooklyn.mementos.LocationMemento;
import brooklyn.util.config.ConfigBag;

/**
 * Information about locations private to Brooklyn.
 */
public interface LocationInternal extends Location, Rebindable {

    @Beta
    public static final ConfigKey<String> SPEC = ConfigKeys.newStringConfigKey("spec", "The spec passed to a location object");
    
    /**
     * Registers the given extension for the given type. If an extension already existed for
     * this type, then this will override it.
     * 
     * @throws NullPointerException if extensionType or extension are null
     * @throws IllegalArgumentException if extension does not implement extensionType
     */
    <T> void addExtension(Class<T> extensionType, T extension);

    /**
     * Get a record of the metadata of this location.
     * <p/>
     * <p>Metadata records are used to record an audit trail of events relating to location usage
     * (for billing purposes, for example). Implementations (and subclasses) should override this
     * method to return information useful for this purpose.</p>
     *
     * @return
     */
    public Map<String, String> toMetadataRecord();

    ConfigBag getLocalConfigBag();

    ConfigBag getAllConfigBag();

    @Override
    RebindSupport<LocationMemento> getRebindSupport();
}
