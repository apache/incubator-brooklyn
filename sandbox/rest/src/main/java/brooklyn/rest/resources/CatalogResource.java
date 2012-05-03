package brooklyn.rest.resources;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.policy.basic.AbstractPolicy;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import com.google.common.collect.Maps;
import com.yammer.dropwizard.logging.Log;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.reflections.Reflections;

@Path("/v1/catalog")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource extends BaseResource {

  private static final Log LOG = Log.forClass(CatalogResource.class);

  private final Set<String> entities;
  private final Set<String> policies;

  public CatalogResource() {
    Reflections reflections = new Reflections("brooklyn");

    entities = loadListOfEntities(reflections);
    policies = loadListOfPolicies(reflections);
  }

  private Set<String> loadListOfPolicies(Reflections reflections) {
    LOG.trace("Building a catalog of policies from the classpath");
    return ImmutableSet.copyOf(transform(filter(
        reflections.getSubTypesOf(AbstractPolicy.class),
        new Predicate<Class<? extends AbstractPolicy>>() {
          @Override
          public boolean apply(Class<? extends AbstractPolicy> aClass) {
            return !Modifier.isAbstract(aClass.getModifiers()) &&
                !aClass.isInterface() && !aClass.isAnonymousClass();
          }
        }),
        new Function<Class<? extends AbstractPolicy>, String>() {
          @Override
          public String apply(Class<? extends AbstractPolicy> aClass) {
            LOG.trace("Found policy '{}'", aClass.getName());
            return aClass.getName();
          }
        }
    ));
  }

  private Set<String> loadListOfEntities(Reflections reflections) {
    LOG.trace("Building a catalog of startable entities from the classpath");
    return ImmutableSet.copyOf(transform(filter(
        reflections.getSubTypesOf(AbstractEntity.class),
        new Predicate<Class<? extends EntityLocal>>() {
          @Override
          public boolean apply(@Nullable Class<? extends EntityLocal> aClass) {
            return !Modifier.isAbstract(aClass.getModifiers()) &&
                !aClass.isInterface() &&
                !aClass.isAnonymousClass();
          }
        }),
        new Function<Class<? extends EntityLocal>, String>() {
          @Override
          public String apply(Class<? extends EntityLocal> aClass) {
            LOG.trace("Found entity '{}'", aClass.getName());
            return aClass.getName();
          }
        }));
  }

  public boolean containsEntity(String entityName) {
    return entities.contains(entityName);
  }

  @GET
  @Path("entities")
  public Iterable<String> listEntities(
      final @QueryParam("name") @DefaultValue("") String name
  ) {
    if ("".equals(name)) {
      return entities;
    } else {
      final String normalizedName = name.toLowerCase();
      return filter(entities, new Predicate<String>() {
        @Override
        public boolean apply(@Nullable String entity) {
          return entity != null && entity.toLowerCase().contains(normalizedName);
        }
      });
    }
  }

  @GET
  @Path("entities/{entity}")
  public Iterable<String> getEntity(@PathParam("entity") String entityType) throws Exception {
    if (!containsEntity(entityType)) {
      throw notFound("Entity with type '%s' not found", entityType);
    }

    try {
      // TODO find a different way to query the list of configuration keys
      // without having to create an instance
      Class<EntityLocal> clazz = (Class<EntityLocal>) Class.forName(entityType);
      Constructor constructor = clazz.getConstructor(new Class[]{Map.class});

      EntityLocal instance = (EntityLocal) constructor.newInstance(Maps.newHashMap());
      return instance.getConfigKeys().keySet();

    } catch (ClassNotFoundException e) {
      throw notFound(e.getMessage());

    } catch (NoSuchMethodException e) {
      throw notFound(e.getMessage());
    }
  }

  @GET
  @Path("policies")
  public Iterable<String> listPolicies() {
    return policies;
  }
}
