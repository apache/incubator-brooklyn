package brooklyn.rest.resources;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.newHashSet;
import com.yammer.metrics.annotation.Timed;
import java.util.Set;
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

  private Set<Class<? extends brooklyn.entity.Entity>> entities;

  public EntityResource() {
    Reflections reflections = new Reflections("brooklyn");
    entities = reflections.getSubTypesOf(brooklyn.entity.Entity.class);
  }

  @GET
  @Timed
  public Set<String> listAvailableEntities(
      final @QueryParam("name") @DefaultValue("") String name
  ) {
    final String normalizedName = name.toLowerCase();
    return newHashSet(transform(filter(entities,
        new Predicate<Class<? extends brooklyn.entity.Entity>>() {
          @Override
          public boolean apply(Class<? extends brooklyn.entity.Entity> aClass) {
            return name.equals("") || aClass.getName().toLowerCase().contains(normalizedName);
          }
        }),
        new Function<Class<? extends brooklyn.entity.Entity>, String>() {
          @Override
          public String apply(Class<? extends brooklyn.entity.Entity> aClass) {
            return aClass.getName();
          }
        }));
  }
}
