package brooklyn.rest.resources;

import brooklyn.BrooklynVersion;

import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiOperation;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/v1/version")
@Api(value = "/v1/version", description = "Get brooklyn version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource extends AbstractBrooklynRestResource {

  @GET
  @ApiOperation(value = "Get brooklyn version", responseClass = "String", multiValueResponse = false)
  public String getVersion() {
    return BrooklynVersion.get();
  }

}
