package io.brooklyn.camp.rest.resource;

import io.brooklyn.camp.dto.PlatformComponentDto;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import brooklyn.rest.apidoc.Apidoc;

import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path(PlatformComponentRestResource.URI_PATH)
@Apidoc("Platform Component resources")
@Produces("application/json")
public class PlatformComponentRestResource extends AbstractCampRestResource {

    public static final String URI_PATH = PlatformRestResource.CAMP_URI_PATH + "/platform-components";

    @Path("/{id}")
    @ApiOperation(value = "Get a specific platform component",
            responseClass = PlatformComponentDto.CLASS_NAME)
    @GET
    public PlatformComponentDto get(
            @ApiParam(value = "ID of item being retrieved", required = true)
            @PathParam("id") String id) {
        return dto().adapt(lookup(camp().platformComponents(), id));
    }

}
