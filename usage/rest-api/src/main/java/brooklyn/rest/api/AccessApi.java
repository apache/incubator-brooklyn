package brooklyn.rest.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.AccessSummary;

import com.google.common.annotations.Beta;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Beta
@Path("/v1/access")
@Apidoc("Access")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AccessApi {

    // TODO First access use-case is to disable location-provisioning (for Citrix's Cloud Portal Business Manager (CPBM)).
    // We rely on location implementations calling m`anagementContext.getAccessController().canProvisionLocation(parent)`,
    // which isn't ideal (because some impls might forget to do this). We can't just do it in the core management-context
    // because things like JcloudsLocation will provision the VM and only then create the JcloudsSshMachineLocation.
    
    @GET
    @ApiOperation(
            value = "Fetch access control summary",
            responseClass = "brooklyn.rest.domain.AccessSummary"
            )
    public AccessSummary get();

    @POST
    @Path("/locationProvisioningAllowed")
    @ApiOperation(value = "Sets whether location provisioning is permitted (beta feature)")
    public Response locationProvisioningAllowed(
            @ApiParam(name = "allowed", value = "Whether allowed or not", required = true)
            @QueryParam("allowed") boolean allowed);
}
