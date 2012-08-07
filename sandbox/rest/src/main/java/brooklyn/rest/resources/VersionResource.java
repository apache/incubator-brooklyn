package brooklyn.rest.resources;

import com.wordnik.swagger.core.*;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/v1/version")
@Api(value = "/v1/version", description = "Get brooklyn version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource extends BaseResource {

  @GET
  @ApiOperation(value = "Get brooklyn version", responseClass = "String", multiValueResponse = false)
  public String getVersion() {
    return "VERSION_STRING_STUB";
  }

}
