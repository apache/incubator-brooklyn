package brooklyn.rest.core;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.jclouds.JcloudsLocation;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import com.google.common.base.Function;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newLinkedList;
import com.google.common.collect.Maps;
import com.yammer.dropwizard.lifecycle.Managed;
import com.yammer.dropwizard.logging.Log;
import java.lang.reflect.Constructor;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

public class ApplicationManager implements Managed {

  private final static Log LOG = Log.forClass(ApplicationManager.class);

  private final LocationStore locationStore;
  private final ConcurrentMap<String, Application> applications;
  private final ExecutorService executorService;

  public ApplicationManager(LocationStore locationStore, ExecutorService executorService) {
    this.locationStore = checkNotNull(locationStore, "locationStore");
    this.executorService = checkNotNull(executorService, "executorService");
    this.applications = Maps.newConcurrentMap();
  }

  @Override
  public void start() throws Exception {
    // TODO load data about running applications from external storage
  }

  @Override
  public void stop() throws Exception {
    destroyAllInBackground(); // TODO or save specs to external storage
  }

  public ConcurrentMap<String, Application> registry() {
    return applications;
  }

  public void startInBackground(final ApplicationSpec spec) {
    LOG.info("Creating application instance for {}", spec);

    final AbstractApplication instance = new AbstractApplication() {
    };

    for (EntitySpec entitySpec : spec.getEntities()) {
      try {
        LOG.info("Creating instance for entity {}", entitySpec.getType());
        Class<Startable> clazz = (Class<Startable>) Class.forName(entitySpec.getType());

        Constructor constructor = clazz.getConstructor(new Class[]{Map.class, brooklyn.entity.Entity.class});
        // TODO parse & rebuild config map as needed
        constructor.newInstance(Maps.newHashMap(entitySpec.getConfig()), instance);

      } catch (Exception e) {
        LOG.error(e, "Failed to create instance for entity {}", entitySpec);
        throw Throwables.propagate(e);
      }
    }

    LOG.info("Adding '{}' application to registry with status ACCEPTED", spec.getName());
    applications.put(spec.getName(), new Application(spec, Application.Status.ACCEPTED, instance));

    // Start all the managed entities by asking the app instance to start in background
    executorService.submit(new Runnable() {

      Function<String, Location> buildLocationFromRef =
          new Function<String, Location>() {
            @Override
            public Location apply(String ref) {
              brooklyn.rest.api.Location location = locationStore.getByRef(ref);
              if (location.getProvider().equals("localhost")) {
                return new LocalhostMachineProvisioningLocation();
              }

              Map<String, String> config = Maps.newHashMap();
              config.put("provider", location.getProvider());
              config.put("identity", location.getIdentity());
              config.put("credential", location.getCredential());
              config.put("providerLocationId", location.getLocation());

              return new JcloudsLocation(config);
            }
          };

      @Override
      public void run() {
        try {
          transitionTo(spec.getName(), Application.Status.STARTING);
          instance.start(newLinkedList(transform(spec.getLocations(), buildLocationFromRef)));
          transitionTo(spec.getName(), Application.Status.RUNNING);

        } catch (Exception e) {
          LOG.error(e, "Failed to start application instance {}", instance);
          transitionTo(spec.getName(), Application.Status.ERROR);

          throw Throwables.propagate(e);
        }
      }
    });
  }

  private void transitionTo(String name, Application.Status status) {
    Application target = applications.get(name);
    LOG.info("Transitioning '{}' application from {} to {}", name, target.getStatus(), status);

    boolean replaced = applications.replace(name, target, target.transitionTo(status));
    if (!replaced) {
      throw new ConcurrentModificationException("Unable to transition '" +
          name + "' application to " + status);
    }
  }

  /**
   * Spawn a background taks to destroy an application
   *
   * @param name ap
   */
  public void destroyInBackground(final String name) {
    if (applications.containsKey(name)) {
      executorService.submit(new Runnable() {
        @Override
        public void run() {
          transitionTo(name, Application.Status.STOPPING);
          AbstractApplication instance = applications.get(name).getInstance();
          try {
            instance.stop();
            applications.remove(name);

          } catch (Exception e) {
            LOG.error(e, "Failed to stop application instance {}", instance);
            transitionTo(name, Application.Status.ERROR);

            throw Throwables.propagate(e);
          }
        }
      });
    }
  }

  /**
   * Destroy all running applications
   */
  public void destroyAllInBackground() {
    for (String name : applications.keySet()) {
      destroyInBackground(name);
    }
  }
}
