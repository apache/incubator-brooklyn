package brooklyn.rest.resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import brooklyn.rest.api.CatalogEntitySummary;
import brooklyn.rest.api.CatalogPolicySummary;
import brooklyn.rest.core.WebResourceUtils;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/catalog")
@Api(value = "/v1/catalog", description = "Manage entities and policies available on the server")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource extends BrooklynResourceBase {


    @POST
    @ApiOperation(value = "Create a new entity by uploading a Groovy script from browser using multipart/form-data",
        responseClass = "String")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response createFromMultipart(
        @ApiParam(name = "groovyCode", value = "multipart/form-data file input field")
        @FormDataParam("groovyCode") InputStream uploadedInputStream,
        @FormDataParam("groovyCode") FormDataContentDisposition fileDetail) throws IOException {

      return brooklyn().getCatalog().createFromGroovyCode(CharStreams.toString(new InputStreamReader(uploadedInputStream, Charsets.UTF_8)));
    }
    
    @POST
    @ApiOperation(value = "Create new entity or policy by uploading a Groovy script", responseClass = "String")
    public Response create(
            @ApiParam(name = "groovyCode", value = "Groovy code for the entity or policy", required = true)
            @Valid String groovyCode
    ) {
        return brooklyn().getCatalog().createFromGroovyCode(groovyCode);
    }

    @GET
    @Path("/entities")
    @ApiOperation(value = "Fetch a list of entities matching a query", responseClass = "String", multiValueResponse = true)
    public Iterable<String> listEntities(
        @ApiParam(name = "name", value = "Query to filter entities by")
        final @QueryParam("name") @DefaultValue("") String name
    ) {
        return brooklyn().getCatalog().listEntitiesMatching(name, false);
    }

    @GET
    @Path("/applications")
    @ApiOperation(value = "Fetch a list of application templates matching a query", responseClass = "String", multiValueResponse = true)
    public Iterable<String> listApplications(
        @ApiParam(name = "name", value = "Query to filter application templates by")
        final @QueryParam("name") @DefaultValue("") String name
    ) {
        return brooklyn().getCatalog().listEntitiesMatching(name, true);
    }

    @GET
    @Path("/entities/{entity}")
    @ApiOperation(value = "Fetch an entity's definition from the catalog", responseClass = "CatalogEntitySummary", multiValueResponse = true)
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public CatalogEntitySummary getEntity(
        @ApiParam(name = "entity", value = "The class name of the entity to retrieve", required = true)
        @PathParam("entity") String entityType) throws Exception {
      if (!brooklyn().getCatalog().containsEntity(entityType)) {
        throw WebResourceUtils.notFound("Entity with type '%s' not found", entityType);
      }

      return CatalogEntitySummary.fromType(brooklyn().getCatalog().getEntityClass(entityType));
    }

    @GET
    @Path("/policies")
    @ApiOperation(value = "List available policies", responseClass = "String", multiValueResponse = true)
    public Iterable<String> listPolicies() {
        return brooklyn().getCatalog().listPolicies();
    }
    
    @GET
    @Path("/policies/{policy}")
    @ApiOperation(value = "Fetch a policy's definition from the catalog", responseClass = "String", multiValueResponse = true)
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public CatalogPolicySummary getPolicy(
        @ApiParam(name = "policy", value = "The class name of the policy to retrieve", required = true)
        @PathParam("policy") String policyType) throws Exception {
      if (!brooklyn().getCatalog().containsPolicy(policyType)) {
        throw WebResourceUtils.notFound("Policy with type '%s' not found", policyType);
      }

      return CatalogPolicySummary.fromType(brooklyn().getCatalog().getPolicyClass(policyType));
    }

}
