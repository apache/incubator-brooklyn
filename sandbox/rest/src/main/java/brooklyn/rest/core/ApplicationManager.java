package brooklyn.rest.core;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.jclouds.JcloudsLocation;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.Entity;
import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newLinkedList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.yammer.dropwizard.lifecycle.Managed;
import java.lang.reflect.Constructor;
import java.util.Map;

public class ApplicationManager implements Managed {

  private final LocationStore locationStore;
  private final Map<String, Application> applications;

  private final Multimap<String, Startable> running;

  public ApplicationManager(LocationStore locationStore) {
    this.locationStore = locationStore;
    this.applications = Maps.newConcurrentMap();
    this.running = HashMultimap.create();
  }

  @Override
  public void start() throws Exception {
    // TODO load data about running applications from external storage
  }

  @Override
  public void stop() throws Exception {
    destroyAll(); // TODO or save specs to external storage
  }

  public void registerAndStart(Application application) {
    applications.put(application.getName(), application);

    // Create an application to server as a context

    Startable context = new AbstractApplication() {
    };

    // Create instances for all entities

    for (Entity entity : application.getEntities()) {
      try {
        Class<Startable> klass = (Class<Startable>) Class.forName(entity.getName());
        Constructor constructor = klass.getConstructor(new Class[]{Map.class, brooklyn.entity.Entity.class});
        constructor.newInstance(Maps.newHashMap(), context);

      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    // Start all the managed entities by asking the application to start

    context.start(newLinkedList(transform(
        application.getLocations(),
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

    running.put(application.getName(), context);
  }

  public void destroy(String application) {
    for (Startable entity : running.get(application)) {
      entity.stop();
    }
    running.removeAll(application);
    applications.remove(application);
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
