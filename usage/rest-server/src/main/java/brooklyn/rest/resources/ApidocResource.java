package brooklyn.rest.resources;

import brooklyn.rest.apidoc.Apidoc;

import javax.ws.rs.Path;

@Apidoc("API info")
@Path("/v1/apidoc")
public class ApidocResource extends brooklyn.rest.apidoc.ApidocResource {

}
