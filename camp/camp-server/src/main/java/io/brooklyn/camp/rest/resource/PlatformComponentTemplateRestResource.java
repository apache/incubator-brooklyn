package io.brooklyn.camp.rest.resource;

import io.brooklyn.camp.dto.PlatformComponentTemplateDto;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import brooklyn.rest.apidoc.Apidoc;

import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path(PlatformComponentTemplateRestResource.URI_PATH)
@Apidoc("Platform Component Template resources")
@Produces("application/json")
public class PlatformComponentTemplateRestResource extends AbstractCampRestResource {

    public static final String URI_PATH = PlatformRestResource.CAMP_URI_PATH + "/platform-component-templates";

    @Path("/{id}")
    @ApiOperation(value = "Get a specific platform component template",
            responseClass = PlatformComponentTemplateDto.CLASS_NAME)
    @GET
    public PlatformComponentTemplateDto get(
            @ApiParam(value = "ID of item being retrieved", required = true)
            @PathParam("id") String id) {
        return dto().adapt(lookup(camp().platformComponentTemplates(), id));
    }

}
