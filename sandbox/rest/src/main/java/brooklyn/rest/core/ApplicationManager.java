package brooklyn.rest.core;

import static brooklyn.rest.core.ApplicationPredicates.status;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.all;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newLinkedList;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

import scala.actors.threadpool.Arrays;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.jclouds.JcloudsLocation;
import brooklyn.management.ManagementContext;
import brooklyn.rest.BrooklynConfiguration;
import brooklyn.rest.api.ApplicationSummary;
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

public class ApplicationManager 
//implements Managed 
{

    //TODO destroy this class
    
//  private final static Log LOG = Log.forClass(ApplicationManager.class);
//
//  private final LocationStore locationStore;
//  private final ConcurrentMap<String, ApplicationSummary> applicationsById;
//  private final ConcurrentMap<String, ApplicationSummary> applicationsByName;
//
//  private final ExecutorService executorService;
//  private final BrooklynConfiguration configuration;
//  private final CatalogResource catalog;
//  private final ManagementContext managementContext;
//
//  public ApplicationManager(
//      BrooklynConfiguration configuration,
//      LocationStore locationStore,
//      CatalogResource catalog,
//      ExecutorService executorService
//  ) throws Exception {
//    this.configuration = checkNotNull(configuration, "configuration");
//    this.locationStore = checkNotNull(locationStore, "locationStore");
//    this.executorService = checkNotNull(executorService, "executorService");
//    this.catalog = checkNotNull(catalog, "catalog");
//    this.applicationsById = Maps.newConcurrentMap();
//    this.applicationsByName = Maps.newConcurrentMap();
//    this.managementContext = configuration.getManagementContextClass().newInstance();
//  }
//
//  public ManagementContext getManagementContext() {
//    return managementContext;
//  }
//  
//  @Override
//  public void start() throws Exception {
//    // TODO load data about running applications from external storage
//  }
//
//  @Override
//  public void stop() throws Exception {
//    if (configuration.isStopApplicationsOnExit()) {
//      destroyAllInBackground();
//      waitForAllApplicationsToStopOrError();
//    }
//    // TODO save data about running applications to external storage
//  }
//
//  private void waitForAllApplicationsToStopOrError() throws InterruptedException {
//    while (applicationsById.size() != 0) {
//      Thread.sleep(2000);
//      if (all(applicationsById.values(), status(ApplicationSummary.Status.ERROR))) {
//        break;
//      }
//    }
//  }
//
//  /** @deprecated use registryByXxx */
//  public ConcurrentMap<String, ApplicationSummary> registry() {
//    return registryByName();
//  }
//  
//  public ConcurrentMap<String, ApplicationSummary> registryById() {
//      return applicationsById;
//  }
//  public ConcurrentMap<String, ApplicationSummary> registryByName() {
//      return applicationsByName;
//  }
//
//  public void injectApplication(final AbstractApplication instance, ApplicationSummary.Status status) {
//    String name = instance.getDisplayName();
//    ApplicationSpec spec = ApplicationSpec.builder().name(name).type(instance.getClass().getCanonicalName()).locations(Collections.<String>emptySet()).build();
//    ApplicationSummary app = new ApplicationSummary(spec, status, instance);
//    applicationsById.put(instance.getId(), app);
//    applicationsByName.put(name, app);
//  }
//
//  @SuppressWarnings("serial")
//public ApplicationSummary startInBackground(final ApplicationSpec spec) {
//    LOG.info("Creating application instance for {}", spec);
//
//    final AbstractApplication instance;
//    
//    try {
//        if (spec.getType()!=null) {
//            instance = (AbstractApplication) newEntityInstance(spec.getType(), null, spec.getConfig());
//        } else {
//            instance = new AbstractApplication() {};
//        }
//        if (spec.getName()!=null && !spec.getName().isEmpty()) instance.setDisplayName(spec.getName());
//
//        if (spec.getEntities()!=null) for (EntitySpec entitySpec : spec.getEntities()) {
//            LOG.info("Creating instance for entity {}", entitySpec.getType());
//            AbstractEntity entity = newEntityInstance(entitySpec.getType(), instance, entitySpec.getConfig());
//            if (entitySpec.getName()!=null && !spec.getName().isEmpty()) entity.setDisplayName(entitySpec.getName());
//        }
//    } catch (Exception e) {
//        LOG.error(e, "Failed to create application: "+e);
//        throw Throwables.propagate(e);
//    }
//
//    LOG.info("Placing '{}' under management", spec.getName());
//    managementContext.manage(instance);
//    
//    LOG.info("Adding '{}' application to registry with status ACCEPTED", spec.getName());
//    ApplicationSummary app = new ApplicationSummary(spec, ApplicationSummary.Status.ACCEPTED, instance);
//    applicationsById.put(instance.getId(), app);
//    applicationsByName.put(spec.getName(), app);
//
//    // Start all the managed entities by asking the app instance to start in background
//    executorService.submit(new Runnable() {
//
//      Function<String, Location> buildLocationFromRef =
//          new Function<String, Location>() {
//            @Override
//            public Location apply(String ref) {
//              LocationSpec locationSpec = locationStore.getByRef(ref);
//              if (locationSpec.getProvider().equals("localhost")) {
//                return new LocalhostMachineProvisioningLocation(MutableMap.copyOf(locationSpec.getConfig()));
//              }
//
//              Map<String, String> config = Maps.newHashMap();
//              config.put("provider", locationSpec.getProvider());
//              config.putAll(locationSpec.getConfig());
//
//              return new JcloudsLocation(config);
//            }
//          };
//
//      @Override
//      public void run() {
//        try {
//          transitionTo(spec.getName(), ApplicationSummary.Status.STARTING);
//          instance.start(newLinkedList(transform(spec.getLocations(), buildLocationFromRef)));
//          transitionTo(spec.getName(), ApplicationSummary.Status.RUNNING);
//
//        } catch (Exception e) {
//          LOG.error(e, "Failed to start application instance {}", instance);
//          transitionTo(spec.getName(), ApplicationSummary.Status.ERROR);
//
//          throw Throwables.propagate(e);
//        }
//      }
//    });
//    
//    return app;
//  }
//
//  private AbstractEntity newEntityInstance(String type, Entity owner, Map<String, String> configO) throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
//    Class<? extends AbstractEntity> clazz = catalog.getEntityClass(type);
//    Map<String, String> config = Maps.newHashMap(configO);
//    Constructor<?>[] constructors = clazz.getConstructors();
//    AbstractEntity result = null;
//    if (owner==null) {
//        result = tryInstantiateEntity(constructors, new Class[] { Map.class }, new Object[] { config });
//        if (result!=null) return result;
//    }
//    result = tryInstantiateEntity(constructors, new Class[] { Map.class, Entity.class }, new Object[] { config, owner });
//    if (result!=null) return result;
//
//    result = tryInstantiateEntity(constructors, new Class[] { Map.class }, new Object[] { config });
//    if (result!=null) {
//        if (owner!=null) ((AbstractEntity)result).setOwner(owner);
//        return result;
//    }
//
//    result = tryInstantiateEntity(constructors, new Class[] { Entity.class }, new Object[] { owner });
//    if (result!=null) {
//        ((AbstractEntity)result).configure(config);
//        return result;
//    }
//
//    result = tryInstantiateEntity(constructors, new Class[] {}, new Object[] {});
//    if (result!=null) {
//        if (owner!=null) ((AbstractEntity)result).setOwner(owner);
//        ((AbstractEntity)result).configure(config);
//        return result;
//    }
//    
//    throw new IllegalStateException("No suitable constructor for instantiating entity "+type);
//  }
//
//  private AbstractEntity tryInstantiateEntity(Constructor<?>[] constructors, Class<?>[] classes, Object[] objects) throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
//    for (Constructor<?> c: constructors) {
//        if (Arrays.equals(c.getParameterTypes(), classes)) {
//            return (AbstractEntity) c.newInstance(objects);
//        }
//    }
//    return null;
//  }
//
//@Deprecated
//  private void transitionTo(String name, ApplicationSummary.Status status) {
//      transitionAppByNameTo(name, status);
//  }
//  
//  private void transitionAppByNameTo(String name, ApplicationSummary.Status status) {
//    ApplicationSummary target = checkNotNull(applicationsByName.get(name), "No application found with name '%'", name);
//    transitionAppByIdTo(target.getInstance().getId(), status);
//  }
//  private void transitionAppByIdTo(String id, ApplicationSummary.Status status) {
//    ApplicationSummary target = checkNotNull(applicationsById.get(id), "No application found with id '%'", id);
//    LOG.info("Transitioning '{}' ({}) application from {} to {}", target.getInstance().getDisplayName(), id, target.getStatus(), status);
//
//    ApplicationSummary newAppState = target.transitionTo(status);
//    boolean replaced = applicationsById.replace(target.getInstance().getId(), target, newAppState); 
//    applicationsByName.put(target.getInstance().getDisplayName(), newAppState);
//    if (!replaced) {
//      throw new ConcurrentModificationException("Unable to transition '" +
//          id + "' application to " + status);
//    }
//  }
//
//  /**
//   * Spawn a background task to destroy an application
//   *
//   * @param name application name
//   */
//  @Deprecated
//  public void destroyInBackground(final String name) {
//      destroyAppByNameInBackground(name);
//  }
//  public void destroyAppByNameInBackground(final String name) {
//    if (applicationsByName.containsKey(name)) {
//      executorService.submit(new Runnable() {
//        @Override
//        public void run() {
//          transitionTo(name, ApplicationSummary.Status.STOPPING);
//          AbstractApplication instance = applicationsByName.get(name).getInstance();
//          try {
//            instance.stop();
//            LOG.info("Removing '{}' application from registry", name);
//            applicationsByName.remove(name);
//            applicationsById.remove(instance.getId());
//
//          } catch (Exception e) {
//            LOG.error(e, "Failed to stop application instance {}", instance);
//            transitionTo(name, ApplicationSummary.Status.ERROR);
//
//            throw Throwables.propagate(e);
//          } finally {
//              managementContext.unmanage(instance);
//          }
//        }
//      });
//    }
//  }
//  public void destroyAppByIdInBackground(final String id) {
//      if (applicationsById.containsKey(id)) {
//        executorService.submit(new Runnable() {
//          @Override
//          public void run() {
//            transitionAppByIdTo(id, ApplicationSummary.Status.STOPPING);
//            AbstractApplication instance = applicationsById.get(id).getInstance();
//            try {
//              instance.stop();
//              LOG.info("Removing '{}' application from registry", id);
//              applicationsById.remove(id);
//              applicationsByName.remove(instance.getDisplayName());
//
//            } catch (Exception e) {
//              LOG.error(e, "Failed to stop application instance {}", instance);
//              transitionAppByIdTo(id, ApplicationSummary.Status.ERROR);
//
//              throw Throwables.propagate(e);
//            } finally {
//                managementContext.unmanage(instance);
//            }
//          }
//        });
//      }
//    }
//
//  /**
//   * Destroy all running applications
//   */
//  public void destroyAllInBackground() {
//    for (String id : applicationsById.keySet()) {
//      destroyAppByIdInBackground(id);
//    }
//  }
//
//  /** Returns an App given id or name; else false */
//  public ApplicationSummary getApp(String idOrName) {
//      ApplicationSummary app = applicationsById.get(idOrName);
//      if (app!=null) return app;
//      return applicationsByName.get(idOrName);
//  }
  
}
