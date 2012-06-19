package brooklyn.rest.resources;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.policy.basic.AbstractPolicy;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.wordnik.swagger.core.*;
import groovy.lang.GroovyClassLoader;
import org.reflections.Reflections;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.Map;

import static com.google.common.collect.Sets.filter;

@Path("/v1/catalog.json")
@Api(value = "/v1/catalog", description = "Manage entities and policies available on the server")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource extends BaseResource {

  private final Map<String, Class<? extends AbstractEntity>> entities = Maps.newConcurrentMap();
  private final Map<String, Class<? extends AbstractPolicy>> policies = Maps.newConcurrentMap();

  public CatalogResource() {
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

  @POST
  @ApiOperation(value = "Create new entity or policy by uploading a Groovy script", responseClass = "String")
  public Response create(
    @ApiParam(value = "Groovy code for the entity or policy", required = true)
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
  public Iterable<String> getEntity(
    @ApiParam(name = "entity", value = "The name of the entity to retrieve", required = true)
    @PathParam("entity") String entityType) throws Exception {
    if (!containsEntity(entityType)) {
      throw notFound("Entity with type '%s' not found", entityType);
    }

    try {
      // TODO find a different way to query the list of configuration keys
      // without having to create an instance
      Class<? extends AbstractEntity> clazz = entities.get(entityType);
      Constructor constructor = clazz.getConstructor(new Class[]{Map.class});

      EntityLocal instance = (EntityLocal) constructor.newInstance(Maps.newHashMap());
      return instance.getConfigKeys().keySet();

    } catch (NoSuchMethodException e) {
      throw notFound(e.getMessage());
    }
  }

  @GET
  @Path("/policies")
  @ApiOperation(value = "List available policies", responseClass = "String", multiValueResponse = true)
  public Iterable<String> listPolicies() {
    return policies.keySet();
  }
}
