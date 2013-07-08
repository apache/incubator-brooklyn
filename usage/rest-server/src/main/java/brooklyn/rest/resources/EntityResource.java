package brooklyn.rest.resources;

import brooklyn.entity.Entity;
import brooklyn.rest.api.EntityApi;
import brooklyn.rest.transform.EntityTransformer;
import brooklyn.rest.domain.EntitySummary;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

import java.util.List;

import static com.google.common.collect.Iterables.transform;

public class EntityResource extends AbstractBrooklynRestResource implements EntityApi {


   @Override
  public List<EntitySummary> list(final String application) {
    return summaryForChildrenEntities(brooklyn().getApplication(application));
  }

    @Override
  public EntitySummary get(String application, String entity) {
    return EntityTransformer.entitySummary(brooklyn().getEntity(application, entity));
  }

  // TODO rename as "/children" ?
  @Override
  public Iterable<EntitySummary> getChildren( final String application, final String entity) {
    return summaryForChildrenEntities(brooklyn().getEntity(application, entity));
  }

  private List<EntitySummary> summaryForChildrenEntities(Entity rootEntity) {
    return Lists.newArrayList(transform(
        rootEntity.getChildren(),
        new Function<Entity, EntitySummary>() {
          @Override
          public EntitySummary apply(Entity entity) {
            return EntityTransformer.entitySummary(entity);
          }
        }));
  }
}
