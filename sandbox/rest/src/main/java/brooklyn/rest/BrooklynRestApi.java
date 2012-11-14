package brooklyn.rest;

import java.util.ArrayList;
import java.util.List;

import brooklyn.rest.resources.ActivityResource;
import brooklyn.rest.resources.ApplicationResource;
import brooklyn.rest.resources.AbstractBrooklynRestResource;
import brooklyn.rest.resources.CatalogResource;
import brooklyn.rest.resources.ConfigResource;
import brooklyn.rest.resources.EffectorResource;
import brooklyn.rest.resources.EntityResource;
import brooklyn.rest.resources.LocationResource;
import brooklyn.rest.resources.PolicyResource;
import brooklyn.rest.resources.SensorResource;
import brooklyn.rest.resources.VersionResource;

public class BrooklynRestApi {

    public static final Iterable<AbstractBrooklynRestResource> getBrooklynRestResources() {
        List<AbstractBrooklynRestResource> resources = new ArrayList<AbstractBrooklynRestResource>();
        resources.add(new LocationResource());
        resources.add(new CatalogResource());
        resources.add(new ApplicationResource());
        resources.add(new EntityResource());
        resources.add(new ConfigResource());
        resources.add(new SensorResource());
        resources.add(new EffectorResource());
        resources.add(new PolicyResource());
        resources.add(new ActivityResource());
        resources.add(new VersionResource());
        return resources;
    }

}
