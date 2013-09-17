package brooklyn.rest.api;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.Statistic;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Date;
import java.util.List;

@Path("/v1/usage")
@Apidoc("Usage")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface UsageApi {

    @GET
    @Path("/applications")
    @ApiOperation(
            value = "Retrieve usage information about all applications",
            responseClass = "brooklyn.rest.domain.ApplicationUsage"
    )
    @ApiErrors(value = {})
    public List<Statistic> listApplicationUsage(
            @ApiParam(
                    name = "start",
                    value = "timestamp of start marker for usage reporting, in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("start") String startDate,
            @ApiParam(
                    name = "end",
                    value = "timestamp of end marker for usage reporting in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("end") String endDate) ;

    @GET
    @Path("/applications/{application}")
    @ApiOperation(
            value = "Retrieve usage information about a specified application",
            responseClass = "brooklyn.rest.domain.ApplicationUsage"
    )
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Application not found")
    })
    public List<Statistic> getApplicationUsage(
            @ApiParam(
                    name = "application",
                    value = "Application id",
                    required = true
            )
            @PathParam("application") String applicationId,
            @ApiParam(
                    name = "start",
                    value = "timestamp of start marker for usage reporting in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("start") String startDate,
            @ApiParam(
                    name = "end",
                    value = "timestamp of end marker for usage reporting in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("end") String endDate) ;

    @GET
    @Path("/machines")
    @ApiOperation(
            value = "Retrieve usage information about machine locations used by the specified application",
            responseClass = "brooklyn.rest.domain.Usage"
    )
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Application not found")
    })
    public List<Statistic> listMachineUsages(
            @ApiParam(
                    name = "application",
                    value = "Application id",
                    required = false
            )
            @QueryParam("application") String application,
            @ApiParam(
                    name = "start",
                    value = "timestamp of start marker for usage reporting in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("start") String startDate,
            @ApiParam(
                    name = "end",
                    value = "timestamp of end marker for usage reporting in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("end") String endDate) ;

}
