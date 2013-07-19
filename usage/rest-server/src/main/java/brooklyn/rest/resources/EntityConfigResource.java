package brooklyn.rest.resources;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.rest.api.EntityConfigApi;
import brooklyn.rest.domain.EntityConfigSummary;
import brooklyn.rest.transform.EntityTransformer;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static com.google.common.collect.Iterables.transform;

public class EntityConfigResource extends AbstractBrooklynRestResource implements EntityConfigApi {

  @Override
  public List<EntityConfigSummary> list(final String application, final String entityToken) {
    final EntityLocal entity = brooklyn().getEntity(application, entityToken);

    return Lists.newArrayList(transform(
        entity.getEntityType().getConfigKeys(),
        new Function<ConfigKey<?>, EntityConfigSummary>() {
          @Override
          public EntityConfigSummary apply(ConfigKey<?> config) {
            return EntityTransformer.entityConfigSummary(entity, config);
          }
        }));
  }

  // TODO support parameters  ?show=value,summary&name=xxx &format={string,json,xml}
  // (and in sensors class)
  @Override
  public Map<String, Object> batchConfigRead(String application, String entityToken) {
    // TODO: add test
    EntityLocal entity = brooklyn().getEntity(application, entityToken);
    Map<ConfigKey<?>, Object> source = ((EntityInternal)entity).getAllConfig();
    Map<String, Object> result = Maps.newLinkedHashMap();
    for (Map.Entry<ConfigKey<?>, Object> ek: source.entrySet()) {
        result.put(ek.getKey().getName(), getValueForDisplay(entity, ek.getValue()));
    }
    return result;
  }

  @Override
  public String get(String application, String entityToken, String configKeyName) {
    EntityLocal entity = brooklyn().getEntity(application, entityToken);
    ConfigKey<?> ck = entity.getEntityType().getConfigKey(configKeyName);
    if (ck==null) ck = new BasicConfigKey<Object>(Object.class, configKeyName);
    return getValueForDisplay(entity, ((AbstractEntity)entity).getConfigMap().getRawConfig(ck));
  }

  private String getValueForDisplay(EntityLocal entity, Object value) {
    return brooklyn().getStringValueForDisplay(value);
  }

}
