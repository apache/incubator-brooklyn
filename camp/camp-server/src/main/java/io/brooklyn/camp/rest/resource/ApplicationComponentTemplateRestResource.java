package io.brooklyn.camp.rest.resource;

import io.brooklyn.camp.dto.ApplicationComponentTemplateDto;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import brooklyn.rest.apidoc.Apidoc;

import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path(ApplicationComponentTemplateRestResource.URI_PATH)
@Apidoc("Application Component Template resources")
@Produces("application/json")
public class ApplicationComponentTemplateRestResource extends AbstractCampRestResource {

    public static final String URI_PATH = PlatformRestResource.CAMP_URI_PATH + "/application-component-templates";

    @Path("/{id}")
    @ApiOperation(value = "Get a specific application component template",
        responseClass = ApplicationComponentTemplateDto.CLASS_NAME)
    @GET
    public ApplicationComponentTemplateDto get(
            @ApiParam(value = "ID of item being retrieved", required = true)
            @PathParam("id") String id) {
        return dto().adapt(lookup(camp().applicationComponentTemplates(), id));
    }

}
