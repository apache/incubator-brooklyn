package brooklyn.rest.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import brooklyn.BrooklynVersion;
import brooklyn.rest.apidoc.Apidoc;

import com.wordnik.swagger.core.ApiOperation;

@Path("/v1/version")
@Apidoc("Version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource extends AbstractBrooklynRestResource {

  @GET
  @ApiOperation(value = "Return version identifier information for this Brooklyn instance", responseClass = "String", multiValueResponse = false)
  public String getVersion() {
    return BrooklynVersion.get();
  }

}
