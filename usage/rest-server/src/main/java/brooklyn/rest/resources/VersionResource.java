package brooklyn.rest.resources;

import brooklyn.BrooklynVersion;
import brooklyn.rest.api.VersionApi;

public class VersionResource extends AbstractBrooklynRestResource implements VersionApi {

  @Override
  public String getVersion() {
    return BrooklynVersion.get();
  }

}
