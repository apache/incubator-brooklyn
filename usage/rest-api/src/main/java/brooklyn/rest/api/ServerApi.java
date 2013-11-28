package brooklyn.rest.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import brooklyn.rest.apidoc.Apidoc;

import com.google.common.annotations.Beta;
import com.wordnik.swagger.core.ApiOperation;

@Path("/v1/server")
@Apidoc("Server")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)

@Beta
public interface ServerApi {

    @POST
    @Path("/shutdown")
    @ApiOperation(value = "Terminate this Brooklyn server instance")
    @Consumes( {MediaType.APPLICATION_FORM_URLENCODED})
    public void shutdown(
        @FormParam("stopAppsFirst") @DefaultValue("false") boolean stopAppsFirst,
        @FormParam("delayMillis") @DefaultValue("250") long delayMillis);

}
