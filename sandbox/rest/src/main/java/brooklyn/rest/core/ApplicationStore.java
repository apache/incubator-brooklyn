package brooklyn.rest.core;

import com.yammer.dropwizard.lifecycle.Managed;

public class ApplicationStore implements Managed {

  private final LocationStore locations;

  public ApplicationStore(LocationStore locations) {
    this.locations = locations;
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
