package io.brooklyn.camp.rest.resource;

import io.brooklyn.camp.dto.AssemblyDto;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import brooklyn.rest.apidoc.Apidoc;

import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path(AssemblyRestResource.URI_PATH)
@Apidoc("Assembly resources")
@Produces("application/json")
public class AssemblyRestResource extends AbstractCampRestResource {

//    private static final Logger log = LoggerFactory.getLogger(AssemblyRestResource.class);
    
    public static final String URI_PATH = PlatformRestResource.CAMP_URI_PATH + "/assemblies";

    @Path("/{id}")
    @ApiOperation(value = "Get a specific assembly",
            responseClass = AssemblyDto.CLASS_NAME)
    @GET
    public AssemblyDto get(
            @ApiParam(value = "ID of item being retrieved", required = true)
            @PathParam("id") String id) {
        return dto().adapt(lookup(camp().assemblies(), id));
    }

}
