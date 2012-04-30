package brooklyn.rest.resources;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Startable;
import brooklyn.policy.basic.AbstractPolicy;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import com.google.common.collect.Maps;
import static com.google.common.collect.Sets.newHashSet;
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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.reflections.Reflections;

@Path("/v1/catalog")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource {

  private static final Log LOG = Log.forClass(CatalogResource.class);

  private Set<String> entities;
  private Set<String> policies;

  public CatalogResource() {
    Reflections reflections = new Reflections("brooklyn");

    cacheListOfEntities(reflections);
    cacheListOfPolicies(reflections);
  }

  private void cacheListOfPolicies(Reflections reflections) {
    LOG.info("Building a catalog of policies from the classpath");
    policies = newHashSet(transform(filter(
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
            LOG.info("Found policy '{}'", aClass.getName());
            return aClass.getName();
          }
        }
    ));
  }

  private void cacheListOfEntities(Reflections reflections) {
    LOG.info("Building a catalog of startable entities from the classpath");
    entities = newHashSet(transform(filter(
        reflections.getSubTypesOf(Startable.class),
        new Predicate<Class<? extends Startable>>() {
          @Override
          public boolean apply(@Nullable Class<? extends Startable> aClass) {
            return !Modifier.isAbstract(aClass.getModifiers()) &&
                !aClass.isInterface() &&
                AbstractEntity.class.isAssignableFrom(aClass) &&
                !aClass.isAnonymousClass();
          }
        }),
        new Function<Class<? extends Startable>, String>() {
          @Override
          public String apply(Class<? extends Startable> aClass) {
            LOG.info("Found entity '{}'", aClass.getName());
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
  public Iterable<String> getEntity(@PathParam("entity") String entityName) throws Exception {
    try {
      Class<EntityLocal> clazz = (Class<EntityLocal>) Class.forName(entityName);
      Constructor constructor = clazz.getConstructor(new Class[]{Map.class});

      EntityLocal instance = (EntityLocal) constructor.newInstance(Maps.newHashMap());
      return instance.getConfigKeys().keySet();

    } catch (ClassNotFoundException e) {
      throw new WebApplicationException(Response.Status.NOT_FOUND);

    } catch (NoSuchMethodException e) {
      throw new WebApplicationException(Response.Status.NOT_FOUND);
    }
  }

  @GET
  @Path("policies")
  public Iterable<String> listPolicies() {
    return policies;
  }
}
