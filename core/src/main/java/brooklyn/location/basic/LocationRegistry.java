package brooklyn.location.basic;

import java.util.Map;

/** @deprecated use {@link BasicLocationRegistry} implementing {@link brooklyn.location.LocationRegistry} */
public class LocationRegistry extends BasicLocationRegistry {

    public LocationRegistry() {
        super();
    }

    @SuppressWarnings("rawtypes")
    public LocationRegistry(Map properties) {
        super(properties);
    }

}
