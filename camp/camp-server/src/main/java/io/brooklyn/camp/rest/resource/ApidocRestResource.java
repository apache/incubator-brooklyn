package io.brooklyn.camp.rest.resource;

import javax.ws.rs.Path;

import brooklyn.rest.apidoc.Apidoc;

@Path(ApidocRestResource.API_URI_PATH)
@Apidoc("Web API Documentation")
public class ApidocRestResource extends brooklyn.rest.apidoc.ApidocResource {

    public static final String API_URI_PATH = PlatformRestResource.CAMP_URI_PATH + "/apidoc";

}
