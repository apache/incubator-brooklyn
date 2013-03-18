package brooklyn.location;

import java.util.Map;

import brooklyn.management.ManagementContext;

/**
 * Defines a location, where the {@link #getSpec()} is like a serialized representation
 * of the location so that Brooklyn can create a corresponding location.
 * 
 * Examples include a complete description (e.g. giving a list of machines in a pool), or
 * a name that matches a named location defined in the brooklyn poperties.
 * 
 * Users are not expected to implement this, or to use the interface directly. See
 * {@link LocationRegistry#resolve(String)} and {@link ManagementContext#getLocationRegistry()}.
 */
public interface LocationDefinition {

    public String getId();
    public String getName();
    public String getSpec();
    public Map<String,Object> getConfig();

}