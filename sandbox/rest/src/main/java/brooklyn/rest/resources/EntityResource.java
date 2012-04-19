package brooklyn.rest.resources;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.trait.Startable;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.newHashSet;
import com.yammer.metrics.annotation.Timed;
import java.lang.reflect.Modifier;
import java.util.Set;
import javax.annotation.Nullable;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.reflections.Reflections;

@Path("/entities")
@Produces(MediaType.APPLICATION_JSON)
public class EntityResource {

  private Set<String> entities;

  public EntityResource() {
    Reflections reflections = new Reflections("brooklyn");

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
            return aClass.getName();
          }
        }));
  }

  public boolean contains(String entityName) {
    return entities.contains(entityName);
  }

  @GET
  @Timed
  public Iterable<String> listAvailableEntities(
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
}
