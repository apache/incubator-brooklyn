package brooklyn.rest.api;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.SensorSummary;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/v1/applications/{application}/entities/{entity}/sensors")
@Apidoc("Entity sensors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SensorApi {

  @GET
  @ApiOperation(value = "Fetch the sensor list for a specific application entity",
      responseClass = "brooklyn.rest.domain.SensorSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity")
  })
  public List<SensorSummary> list(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") final String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") final String entityToken
  ) ;

  @GET
  @Path("/current-state")
  @ApiOperation(value = "Fetch sensor values in batch", notes="Returns a map of sensor name to value")
  public Map<String, Object> batchSensorRead(
          @ApiParam(value = "Application ID or name", required = true)
          @PathParam("application") final String application,
          @ApiParam(value = "Entity ID or name", required = true)
          @PathParam("entity") final String entityToken
      ) ;

  @GET
  @Path("/{sensor}")
  @ApiOperation(value = "Fetch sensor value", responseClass = "String")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or sensor")
  })
  @Produces("text/plain")
  public String get(
          @ApiParam(value = "Application ID or name", required = true)
          @PathParam("application") final String application,
          @ApiParam(value = "Entity ID or name", required = true)
          @PathParam("entity") final String entityToken,
          @ApiParam(value = "Sensor name", required = true)
          @PathParam("sensor") String sensorName
  ) ;


}
