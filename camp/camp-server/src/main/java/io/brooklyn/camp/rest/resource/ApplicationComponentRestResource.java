package io.brooklyn.camp.rest.resource;

import io.brooklyn.camp.dto.ApplicationComponentDto;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import brooklyn.rest.apidoc.Apidoc;

import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path(ApplicationComponentRestResource.URI_PATH)
@Apidoc("Application Component resources")
@Produces("application/json")
public class ApplicationComponentRestResource extends AbstractCampRestResource {

    public static final String URI_PATH = PlatformRestResource.CAMP_URI_PATH + "/application-components";

    @Path("/{id}")
    @ApiOperation(value = "Get a specific application component",
        responseClass = ApplicationComponentDto.CLASS_NAME)
    @GET
    public ApplicationComponentDto get(
            @ApiParam(value = "ID of item being retrieved", required = true)
            @PathParam("id") String id) {
        return dto().adapt(lookup(camp().applicationComponents(), id));
    }

}
