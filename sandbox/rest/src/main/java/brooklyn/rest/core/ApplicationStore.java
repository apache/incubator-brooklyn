package brooklyn.rest.core;

import brooklyn.rest.api.Application;
import com.google.common.collect.Maps;
import com.yammer.dropwizard.lifecycle.Managed;
import java.util.Map;

public class ApplicationStore implements Managed {

  private final LocationStore locations;
  private final Map<String, Application> applications;

  public ApplicationStore(LocationStore locations) {
    this.locations = locations;
    this.applications = Maps.newConcurrentMap();
  }

  @Override
  public void start() throws Exception {
    // TODO load data about applications from local storage
  }

  @Override
  public void stop() throws Exception {
    //To change body of implemented methods use File | Settings | File Templates.
  }
}
