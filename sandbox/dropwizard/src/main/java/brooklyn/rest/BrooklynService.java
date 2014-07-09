package brooklyn.rest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.auth.BasicAuthFilter;
import brooklyn.rest.auth.ConfigBasedAuthenticator;
import brooklyn.rest.auth.User;
import brooklyn.rest.commands.applications.DeleteApplicationCommand;
import brooklyn.rest.commands.applications.InvokeEffectorCommand;
import brooklyn.rest.commands.applications.ListApplicationsCommand;
import brooklyn.rest.commands.applications.ListEffectorsCommand;
import brooklyn.rest.commands.applications.QuerySensorsCommand;
import brooklyn.rest.commands.applications.StartApplicationCommand;
import brooklyn.rest.commands.catalog.CatalogEntityDetailsCommand;
import brooklyn.rest.commands.catalog.ListCatalogEntitiesCommand;
import brooklyn.rest.commands.catalog.ListCatalogPoliciesCommand;
import brooklyn.rest.commands.catalog.LoadClassCommand;
import brooklyn.rest.commands.locations.AddLocationCommand;
import brooklyn.rest.commands.locations.ListLocationsCommand;
import brooklyn.rest.health.GeneralHealthCheck;
import brooklyn.rest.resources.AbstractBrooklynRestResource;
import brooklyn.rest.util.DefaultExceptionMapper;
import brooklyn.rest.views.SwaggerUiResource;
import brooklyn.rest.views.WebClientResource;

import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilderSpec;
import com.yammer.dropwizard.Service;
import com.yammer.dropwizard.auth.Authenticator;
import com.yammer.dropwizard.auth.basic.BasicAuthProvider;
import com.yammer.dropwizard.auth.basic.BasicCredentials;
import com.yammer.dropwizard.bundles.AssetsBundle;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.views.ViewBundle;

/**
 * Application entry point. Configures and starts the embedded web-server.
 * Adds the commands the service exposes to the outside world.
 */
public class BrooklynService extends Service<BrooklynConfiguration> {

  private volatile Environment environment;
  private volatile ManagementContext managementContext = new LocalManagementContext();

  private final CountDownLatch initialized = new CountDownLatch(1);

  protected BrooklynService() {
    super("brooklyn-rest");
    AssetsBundle ab = new AssetsBundle("/assets", CacheBuilderSpec.disableCaching());
    addBundle(ab);
    addBundle(new ViewBundle());
  }

  public ManagementContext getManagementContext() {
    return managementContext;
  }

  @Deprecated
  public static final Iterable<AbstractBrooklynRestResource> getBrooklynRestResources() {
      return BrooklynRestApi.getBrooklynRestResources();
  }
  
  @Override
  protected void initialize(BrooklynConfiguration configuration, Environment environment)
      throws Exception {

    this.environment = environment;

    // Create managed components and wire them together

//    LocationStore locationStore = new LocationStore(configuration);
//    environment.manage(locationStore);
//
//    ExecutorConfiguration executorConfig = configuration.getExecutorConfiguration();
//    ExecutorService managedExecutor = environment.managedExecutorService("brooklyn",
//        executorConfig.getCorePoolSize(), executorConfig.getMaximumPoolSize(),
//        executorConfig.getKeepAliveTimeInSeconds(), TimeUnit.SECONDS);

    for (AbstractBrooklynRestResource r: getBrooklynRestResources()) {
        r.injectManagementContext(getManagementContext());
        environment.addResource(r);
    }
    
    environment.addResource(new SwaggerUiResource());
    environment.addResource(new WebClientResource());

    environment.addHealthCheck(new GeneralHealthCheck());

    if (configuration.getUsers().size() > 0) {
      Authenticator<BasicCredentials, User> authenticator = new ConfigBasedAuthenticator(configuration.getUsers());

      /* protect all the resources exposed by this servlet container */
      environment.addFilter(new BasicAuthFilter<User>(authenticator, configuration.getAuthBasicRealm()), "/*");
      environment.addProvider(new BasicAuthProvider<User>(authenticator, configuration.getAuthBasicRealm()));
    }

    environment.addProvider(new DefaultExceptionMapper());

    initialized.countDown();
  }

  public void runAsync(final String[] args) throws InterruptedException {
    final AtomicReference<Throwable> err = new AtomicReference<Throwable>();
    new Thread("brooklyn-rest") {
      public void run() {
        try {
          BrooklynService.this.run(args);
        } catch (Throwable e) {
          err.set(e);
          initialized.countDown();
          throw Throwables.propagate(e);
        }
      }
    }.start();


    initialized.await();
    if (err.get() != null) {
      throw Throwables.propagate(err.get());
    }
  }

  public void stop() throws Exception {
    if (environment != null) {
      environment.stop();
    }
  }

  public static void main(String[] args) throws Exception {
    BrooklynService service = newBrooklynService();
    service.run(args);
  }

  public static BrooklynService newBrooklynService() throws Exception {
    BrooklynService service = new BrooklynService();
    service.addCommand(new ListApplicationsCommand());
    service.addCommand(new StartApplicationCommand());
    service.addCommand(new DeleteApplicationCommand());

    service.addCommand(new QuerySensorsCommand());
    service.addCommand(new ListEffectorsCommand());
    service.addCommand(new InvokeEffectorCommand());

    service.addCommand(new ListLocationsCommand());
    service.addCommand(new AddLocationCommand());

    service.addCommand(new CatalogEntityDetailsCommand());
    service.addCommand(new ListCatalogEntitiesCommand());
    service.addCommand(new ListCatalogPoliciesCommand());
    service.addCommand(new LoadClassCommand());

    return service;
  }
}
