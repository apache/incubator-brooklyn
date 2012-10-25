package brooklyn.rest.resources;

import static com.google.common.collect.Sets.filter;
import groovy.lang.GroovyClassLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
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

import org.reflections.Reflections;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityTypes;
import brooklyn.policy.basic.AbstractPolicy;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
public class CatalogResource extends BaseResource {

  private final Map<String, Class<? extends AbstractEntity>> entities = Maps.newConcurrentMap();
  private final Map<String, Class<? extends AbstractPolicy>> policies = Maps.newConcurrentMap();

  public CatalogResource() {
    // TODO allow other prefixes to be supplied?
    entities.putAll(buildMapOfSubTypesOf("brooklyn", AbstractEntity.class));
    policies.putAll(buildMapOfSubTypesOf("brooklyn", AbstractPolicy.class));
  }

  private <T> Map<String, Class<? extends T>> buildMapOfSubTypesOf(String prefix, Class<T> clazz) {
    Reflections reflections = new Reflections(prefix);
    ImmutableMap.Builder<String, Class<? extends T>> builder = ImmutableMap.builder();
    for (Class<? extends T> candidate : reflections.getSubTypesOf(clazz)) {
      if (!Modifier.isAbstract(candidate.getModifiers()) &&
          !candidate.isInterface() &&
          !candidate.isAnonymousClass()) {
        builder.put(candidate.getName(), candidate);
      }
    }
    return builder.build();
  }

  public boolean containsEntity(String entityName) {
    return entities.containsKey(entityName);
  }

  public Class<? extends AbstractEntity> getEntityClass(String entityName) {
    return entities.get(entityName);
  }

  public Class<? extends AbstractPolicy> getPolicyClass(String policyName) {
    return policies.get(policyName);
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  @POST
  @ApiOperation(value = "Create new entity or policy by uploading a Groovy script", responseClass = "String")
  public Response create(
      @ApiParam(name = "groovyCode", value = "Groovy code for the entity or policy", required = true)
      @Valid String groovyCode) {
    ClassLoader parent = getClass().getClassLoader();
    GroovyClassLoader loader = new GroovyClassLoader(parent);

    Class clazz = loader.parseClass(groovyCode);

    if (AbstractEntity.class.isAssignableFrom(clazz)) {
      entities.put(clazz.getName(), clazz);
      return Response.created(URI.create("entities/" + clazz.getName())).build();

    } else if (AbstractPolicy.class.isAssignableFrom(clazz)) {
      policies.put(clazz.getName(), clazz);
      return Response.created(URI.create("policies/" + clazz.getName())).build();
    }

    return Response.ok().build();
  }

  @POST
  @ApiOperation(value = "Create a new entity by uploading a Groovy script from browser using multipart/form-data",
      responseClass = "String")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response createFromMultipart(
      @ApiParam(name = "groovyCode", value = "multipart/form-data file input field")
      @FormDataParam("groovyCode") InputStream uploadedInputStream,
      @FormDataParam("groovyCode") FormDataContentDisposition fileDetail) throws IOException {

    return create(CharStreams.toString(new InputStreamReader(uploadedInputStream, Charsets.UTF_8)));
  }

  @GET
  @Path("/entities")
  @ApiOperation(value = "Fetch a list of entities matching a query", responseClass = "String", multiValueResponse = true)
  public Iterable<String> listEntities(
      @ApiParam(name = "name", value = "Query to filter entities by")
      final @QueryParam("name") @DefaultValue("") String name
  ) {
    if ("".equals(name)) {
      return entities.keySet();
    } else {
      final String normalizedName = name.toLowerCase();
      return filter(entities.keySet(), new Predicate<String>() {
        @Override
        public boolean apply(@Nullable String entity) {
          return entity != null && entity.toLowerCase().contains(normalizedName);
        }
      });
    }
  }

  @GET
  @Path("/entities/{entity}")
  @ApiOperation(value = "Fetch an entity", responseClass = "String", multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Entity not found")
  })
  public List<String> getEntity(
      @ApiParam(name = "entity", value = "The name of the entity to retrieve", required = true)
      @PathParam("entity") String entityType) throws Exception {
    if (!containsEntity(entityType)) {
      throw notFound("Entity with type '%s' not found", entityType);
    }

    return Lists.newArrayList(EntityTypes.getDefinedConfigKeys(entityType).keySet());
  }

  @GET
  @Path("/policies")
  @ApiOperation(value = "List available policies", responseClass = "String", multiValueResponse = true)
  public Iterable<String> listPolicies() {
    return policies.keySet();
  }
}
