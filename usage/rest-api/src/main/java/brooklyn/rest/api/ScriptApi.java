package brooklyn.rest.api;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.ScriptExecutionSummary;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/v1/script")
@Apidoc(value="Script")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ScriptApi {
    
    public static final String USER_DATA_MAP_SESSION_ATTRIBUTE = "brooklyn.script.groovy.user.data";
    public static final String USER_LAST_VALUE_SESSION_ATTRIBUTE = "brooklyn.script.groovy.user.last";
    
    @SuppressWarnings("rawtypes")
    @POST
    @Path("/groovy")
    @Consumes("application/text")
    @ApiOperation(value = "Execute a groovy script",
        responseClass = "brooklyn.rest.domain.SensorSummary")
    public ScriptExecutionSummary groovy(
            @Context HttpServletRequest request,
            @ApiParam(name = "script", value = "Groovy script to execute", required = true)
            String script
            ) ;

}
