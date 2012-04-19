package brooklyn.rest.core;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.jclouds.JcloudsLocation;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableMap;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newLinkedList;
import com.google.common.collect.Maps;
import com.yammer.dropwizard.lifecycle.Managed;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Set;

public class ApplicationManager implements Managed {

  private final LocationStore locationStore;
  private final Map<String, ApplicationSpec> applications;

  public ApplicationManager(LocationStore locationStore) {
    this.locationStore = locationStore;
    this.applications = Maps.newConcurrentMap();
  }

  @Override
  public void start() throws Exception {
    // TODO load data about running applications from external storage
  }

  @Override
  public void stop() throws Exception {
    destroyAll(); // TODO or save specs to external storage
  }

  public ApplicationSpec getSpec(String name) {
    return applications.get(name);
  }

  public Iterable<ApplicationSpec> entries() {
    return applications.values();
  }

  public void createInstanceAndStart(ApplicationSpec applicationSpec) {
    // Create an Brooklyn application instance to server as a context
    AbstractApplication context = new AbstractApplication() {
    };

    // Create instances for all entities
    for (EntitySpec entitySpec : applicationSpec.getEntities()) {
      try {
        Class<Startable> klass = (Class<Startable>) Class.forName(entitySpec.getType());
        Constructor constructor = klass.getConstructor(new Class[]{Map.class, brooklyn.entity.Entity.class});
        // TODO parse & rebuild config map as needed
        constructor.newInstance(Maps.newHashMap(entitySpec.getConfig()), context);

      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    // Start all the managed entities by asking the applicationSpec to start

    context.start(newLinkedList(transform(
        applicationSpec.getLocations(),
        new Function<String, Location>() {
          @Override
          public Location apply(String ref) {
            brooklyn.rest.api.Location location = locationStore.getByRef(ref);
            if (location.getProvider().equals("localhost")) {
              return new LocalhostMachineProvisioningLocation();
            }
            return new JcloudsLocation(ImmutableMap.of(
                "provider", location.getProvider(),
                "identity", location.getIdentity(),
                "credential", location.getCredential(),
                "providerLocationId", location.getLocation()
            ));
          }
        })));

    applicationSpec.setDeployedContext(context);
    applications.put(applicationSpec.getName(), applicationSpec);
  }

  public void destroy(String application) {
    if (application.contains(application)) {
      applications.get(application).getDeployedContext().stop();
      applications.remove(application);
    }
  }

  /**
   * Destroy all running applications
   */
  public void destroyAll() {
    for (String name : applications.keySet()) {
      destroy(name);
    }
  }
}
