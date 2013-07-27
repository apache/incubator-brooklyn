package brooklyn.rest.api;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.domain.CatalogItemSummary;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Path("/v1/catalog")
@Apidoc("Catalog")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface CatalogApi {

    @POST
    @ApiOperation(value = "Add a new entity or policy type to the catalog by uploading a Groovy script from browser using multipart/form-data",
        responseClass = "String")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response createFromMultipart(
        @ApiParam(name = "groovyCode", value = "multipart/form-data file input field")
        @FormDataParam("groovyCode") InputStream uploadedInputStream,
        @FormDataParam("groovyCode") FormDataContentDisposition fileDetail) throws IOException ;
    
    @POST
    @ApiOperation(value = "Add a new entity or policy type by uploading a Groovy script", responseClass = "String")
    public Response create(
            @ApiParam(name = "groovyCode", value = "Groovy code for the entity or policy", required = true)
            @Valid String groovyCode
    ) ;

    @GET
    @Path("/entities")
    @ApiOperation(value = "List available entity types optionally matching a query", responseClass = "CatalogItemSummary", multiValueResponse = true)
    public List<CatalogItemSummary> listEntities(
        @ApiParam(name = "regex", value = "Regular expression to search for")
        final @QueryParam("regex") @DefaultValue("") String regex,
        @ApiParam(name = "fragment", value = "Substring case-insensitive to search for")
        final @QueryParam("fragment") @DefaultValue("") String fragment
    ) ;

    @GET
    @Path("/applications")
    @ApiOperation(value = "Fetch a list of application templates optionally matching a query", responseClass = "CatalogItemSummary", multiValueResponse = true)
    public List<CatalogItemSummary> listApplications(
            @ApiParam(name = "regex", value = "Regular expression to search for")
            final @QueryParam("regex") @DefaultValue("") String regex,
            @ApiParam(name = "fragment", value = "Substring case-insensitive to search for")
            final @QueryParam("fragment") @DefaultValue("") String fragment
    ) ;

    @GET
    @Path("/entities/{entityId}")
    @ApiOperation(value = "Fetch an entity's definition from the catalog", responseClass = "CatalogEntitySummary", multiValueResponse = true)
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public CatalogEntitySummary getEntity(
        @ApiParam(name = "entityId", value = "The ID of the entity or template to retrieve", required = true)
        @PathParam("entityId") String entityId) throws Exception ;

    @GET
    @Path("/policies")
    @ApiOperation(value = "List available policies optionally matching a query", responseClass = "CatalogItemSummary", multiValueResponse = true)
    public List<CatalogItemSummary> listPolicies(
            @ApiParam(name = "regex", value = "Regular expression to search for")
            final @QueryParam("regex") @DefaultValue("") String regex,
            @ApiParam(name = "fragment", value = "Substring case-insensitive to search for")
            final @QueryParam("fragment") @DefaultValue("") String fragment
    ) ;
    
    @GET
    @Path("/policies/{policyId}")
    @ApiOperation(value = "Fetch a policy's definition from the catalog", responseClass = "CatalogItemSummary", multiValueResponse = true)
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public CatalogItemSummary getPolicy(
        @ApiParam(name = "policyId", value = "The ID of the policy to retrieve", required = true)
        @PathParam("policyId") String policyId) throws Exception ;
    
    @GET
    @Path("/icon/{itemId}")
    @ApiOperation(value = "Return the icon for a given catalog entry (application/image or HTTP redirect)")
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Item not found")
        })
    @Produces("application/image")
    public Response getIcon(
        @ApiParam(name = "itemId", value = "ID of catalog item (application, entity, policy)")
        final @PathParam("itemId") @DefaultValue("") String itemId
    ) ;

}

