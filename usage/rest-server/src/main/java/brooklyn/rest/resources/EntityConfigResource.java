package brooklyn.rest.resources;

import static com.google.common.collect.Iterables.transform;

import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.rest.api.EntityConfigApi;
import brooklyn.rest.domain.EntityConfigSummary;
import brooklyn.rest.transform.EntityTransformer;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

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
        result.put(ek.getKey().getName(), getValueForDisplay(ek.getValue(), true, false));
    }
    return result;
  }

  @Override
  public Object get(String application, String entityToken, String configKeyName) {
      return get(true, application, entityToken, configKeyName);
  }
  
  @Override
  public String getPlain(String application, String entityToken, String configKeyName) {
      return (String)get(true, application, entityToken, configKeyName);
  }
  
  public Object get(boolean preferJson, String application, String entityToken, String configKeyName) {
    EntityLocal entity = brooklyn().getEntity(application, entityToken);
    ConfigKey<?> ck = entity.getEntityType().getConfigKey(configKeyName);
    if (ck==null) ck = new BasicConfigKey<Object>(Object.class, configKeyName);
    
    return getValueForDisplay(entity.getConfigRaw(ck, true).orNull(), preferJson, true);
  }

}
