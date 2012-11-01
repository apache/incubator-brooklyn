package brooklyn.rest.resources;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiOperation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URL;

@Path("/")
@Api(value = "/", description = "Loads the javascript client for this web service")
@Produces(MediaType.TEXT_HTML)
public class WebClientResource extends BaseResource {

  @GET
  @ApiOperation(value = "JavaScript client GUI page")
  public Response backboneUi() {
    String pageContent = "";
    try {
      URL clientPage = Resources.getResource("assets/index.html");
      pageContent = Resources.toString(clientPage, Charsets.UTF_8);
    } catch (IOException e) {
      return Response.serverError().build();
    }
    return Response.ok(pageContent).build();
  }
}
