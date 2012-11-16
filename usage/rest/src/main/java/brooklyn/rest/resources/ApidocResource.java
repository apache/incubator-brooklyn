package brooklyn.rest.resources;

import javax.ws.rs.Path;

import brooklyn.rest.apidoc.Apidoc;

@Path("/v1/apidoc")
@Apidoc("API info")
public class ApidocResource extends brooklyn.rest.apidoc.ApidocResource {

}
