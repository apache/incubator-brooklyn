package brooklyn.rest.resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.entity.Entity;
import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.domain.CatalogItemSummary;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.text.StringPredicates;
import brooklyn.util.text.Strings;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/catalog")
@Apidoc("Catalog")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource extends AbstractBrooklynRestResource {


    @POST
    @ApiOperation(value = "Add a new entity or policy type to the catalog by uploading a Groovy script from browser using multipart/form-data",
        responseClass = "String")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response createFromMultipart(
        @ApiParam(name = "groovyCode", value = "multipart/form-data file input field")
        @FormDataParam("groovyCode") InputStream uploadedInputStream,
        @FormDataParam("groovyCode") FormDataContentDisposition fileDetail) throws IOException {

      return brooklyn().createCatalogEntryFromGroovyCode(CharStreams.toString(new InputStreamReader(uploadedInputStream, Charsets.UTF_8)));
    }
    
    @POST
    @ApiOperation(value = "Add a new entity or policy type by uploading a Groovy script", responseClass = "String")
    public Response create(
            @ApiParam(name = "groovyCode", value = "Groovy code for the entity or policy", required = true)
            @Valid String groovyCode
    ) {
        return brooklyn().createCatalogEntryFromGroovyCode(groovyCode);
    }

    @GET
    @Path("/entities")
    @ApiOperation(value = "List available entity types optionally matching a query", responseClass = "String", multiValueResponse = true)
    public List<String> listEntities(
        @ApiParam(name = "regex", value = "Regular expression to search for")
        final @QueryParam("regex") @DefaultValue("") String regex,
        @ApiParam(name = "fragment", value = "Substring case-insensitive to search for")
        final @QueryParam("fragment") @DefaultValue("") String fragment
    ) {
        return getCatalogItemIdsMatchingRegexFragment(CatalogPredicates.IS_ENTITY, regex, fragment);
    }

    @GET
    @Path("/applications")
    @ApiOperation(value = "Fetch a list of application templates optionally matching a query", responseClass = "String", multiValueResponse = true)
    public List<String> listApplications(
            @ApiParam(name = "regex", value = "Regular expression to search for")
            final @QueryParam("regex") @DefaultValue("") String regex,
            @ApiParam(name = "fragment", value = "Substring case-insensitive to search for")
            final @QueryParam("fragment") @DefaultValue("") String fragment
    ) {
        return getCatalogItemIdsMatchingRegexFragment(CatalogPredicates.IS_TEMPLATE, regex, fragment);
    }

    @SuppressWarnings("unchecked")
    @GET
    @Path("/entities/{entityId}")
    @ApiOperation(value = "Fetch an entity's definition from the catalog", responseClass = "CatalogEntitySummary", multiValueResponse = true)
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public CatalogEntitySummary getEntity(
        @ApiParam(name = "entityId", value = "The ID of the entity or template to retrieve", required = true)
        @PathParam("entityId") String entityId) throws Exception {
      CatalogItem<?> result = brooklyn().getCatalog().getCatalogItem(entityId);
      if (result==null) {
        throw WebResourceUtils.notFound("Entity with id '%s' not found", entityId);
      }

      return CatalogEntitySummary.from(brooklyn(), (CatalogItem<? extends Entity>) result);
    }

    @GET
    @Path("/policies")
    @ApiOperation(value = "List available policies optionally matching a query", responseClass = "String", multiValueResponse = true)
    public List<String> listPolicies(
            @ApiParam(name = "regex", value = "Regular expression to search for")
            final @QueryParam("regex") @DefaultValue("") String regex,
            @ApiParam(name = "fragment", value = "Substring case-insensitive to search for")
            final @QueryParam("fragment") @DefaultValue("") String fragment
    ) {
        return getCatalogItemIdsMatchingRegexFragment(CatalogPredicates.IS_POLICY, regex, fragment);
    }
    
    @GET
    @Path("/policies/{policy}")
    @ApiOperation(value = "Fetch a policy's definition from the catalog", responseClass = "CatalogItemSummary", multiValueResponse = true)
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public CatalogItemSummary getPolicy(
        @ApiParam(name = "policyId", value = "The ID of the policy to retrieve", required = true)
        @PathParam("policyId") String policyId) throws Exception {
        CatalogItem<?> result = brooklyn().getCatalog().getCatalogItem(policyId);
        if (result==null) {
          throw WebResourceUtils.notFound("Policy with id '%s' not found", policyId);
        }

        return CatalogItemSummary.from( result );
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T> List<String> getCatalogItemIdsMatchingRegexFragment(Predicate<CatalogItem<T>> type, String regex, String fragment) {
        List filters = new ArrayList();
        filters.add(type);
        if (Strings.isNonEmpty(regex))
            filters.add(CatalogPredicates.xml(StringPredicates.containsRegex(regex)));
        if (Strings.isNonEmpty(fragment))
            filters.add(CatalogPredicates.xml(StringPredicates.containsLiteralCaseInsensitive(fragment)));
        return ImmutableList.copyOf(Iterables.transform(
                brooklyn().getCatalog().getCatalogItems(Predicates.and(filters)),
                CatalogPredicates.ID_OF_ITEM_TRANSFORMER));        
    }
    

}
