package brooklyn.rest.core;

import brooklyn.rest.api.Application;
import com.google.common.collect.Maps;
import com.yammer.dropwizard.lifecycle.Managed;
import java.util.Map;

public class ApplicationManager implements Managed {

  private final LocationStore locations;
  private final Map<String, Application> applications;

  public ApplicationManager(LocationStore locations) {
    this.locations = locations;
    this.applications = Maps.newConcurrentMap();
  }

  @Override
  public void start() throws Exception {
    // TODO load data about running applications from external storage
  }

  @Override
  public void stop() throws Exception {
    // TODO destroy running applications
  }

  public void registerAndStart(Application application) {
    applications.put(application.getName(), application);
    // TODO create managed entities based on the app spec
  }

  public void destroy(String application) {
    // TODO destroy running entities and unregister
  }
}
