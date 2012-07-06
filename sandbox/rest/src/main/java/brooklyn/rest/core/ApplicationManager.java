package brooklyn.rest.core;

import static brooklyn.rest.core.ApplicationPredicates.status;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.all;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newLinkedList;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.jclouds.JcloudsLocation;
import brooklyn.rest.BrooklynConfiguration;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.api.LocationSpec;
import brooklyn.rest.resources.CatalogResource;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.yammer.dropwizard.lifecycle.Managed;
import com.yammer.dropwizard.logging.Log;

public class ApplicationManager implements Managed {

  private final static Log LOG = Log.forClass(ApplicationManager.class);

  private final LocationStore locationStore;
  private final ConcurrentMap<String, Application> applications;

  private final ExecutorService executorService;
  private final BrooklynConfiguration configuration;
  private final CatalogResource catalog;

  public ApplicationManager(
      BrooklynConfiguration configuration,
      LocationStore locationStore,
      CatalogResource catalog,
      ExecutorService executorService
  ) {
    this.configuration = checkNotNull(configuration, "configuration");
    this.locationStore = checkNotNull(locationStore, "locationStore");
    this.executorService = checkNotNull(executorService, "executorService");
    this.catalog = checkNotNull(catalog, "catalog");
    this.applications = Maps.newConcurrentMap();
  }

  @Override
  public void start() throws Exception {
    // TODO load data about running applications from external storage
  }

  @Override
  public void stop() throws Exception {
    if (configuration.isStopApplicationsOnExit()) {
      destroyAllInBackground();
      waitForAllApplicationsToStopOrError();
    }
    // TODO save data about running applications to external storage
  }

  private void waitForAllApplicationsToStopOrError() throws InterruptedException {
    while (applications.size() != 0) {
      Thread.sleep(2000);
      if (all(applications.values(), status(Application.Status.ERROR))) {
        break;
      }
    }
  }

  public ConcurrentMap<String, Application> registry() {
    return applications;
  }


  public void injectApplication(final AbstractApplication instance, Application.Status status) {
    String name = instance.getDisplayName();
    ApplicationSpec spec = new ApplicationSpec(name, Collections.<EntitySpec>emptySet(), Collections.<String>emptySet());
    applications.put(name, new Application(spec, status, instance));
  }

  public void startInBackground(final ApplicationSpec spec) {
    LOG.info("Creating application instance for {}", spec);

    final AbstractApplication instance = new AbstractApplication() {
    };
    instance.setDisplayName(spec.getName());

    for (EntitySpec entitySpec : spec.getEntities()) {
      try {
        LOG.info("Creating instance for entity {}", entitySpec.getType());
        Class<? extends AbstractEntity> clazz = catalog.getEntityClass(entitySpec.getType());

        Constructor constructor = clazz.getConstructor(new Class[]{Map.class, brooklyn.entity.Entity.class});

        // TODO parse & rebuild config map as needed
        Map<String, String> config = Maps.newHashMap(entitySpec.getConfig());
        config.put("displayName", entitySpec.getName());

        constructor.newInstance(config, instance);

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
              LocationSpec locationSpec = locationStore.getByRef(ref);
              if (locationSpec.getProvider().equals("localhost")) {
                return new LocalhostMachineProvisioningLocation(MutableMap.copyOf(locationSpec.getConfig()));
              }

              Map<String, String> config = Maps.newHashMap();
              config.put("provider", locationSpec.getProvider());
              config.putAll(locationSpec.getConfig());

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
    Application target = checkNotNull(applications.get(name), "No application found with name '%'", name);
    LOG.info("Transitioning '{}' application from {} to {}", name, target.getStatus(), status);

    boolean replaced = applications.replace(name, target, target.transitionTo(status));
    if (!replaced) {
      throw new ConcurrentModificationException("Unable to transition '" +
          name + "' application to " + status);
    }
  }

  /**
   * Spawn a background task to destroy an application
   *
   * @param name application name
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
            LOG.info("Removing '{}' application from registry", name);
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
