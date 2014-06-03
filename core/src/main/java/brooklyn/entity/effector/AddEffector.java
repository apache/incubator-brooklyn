package brooklyn.entity.effector;

import java.util.Collections;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.Effectors.EffectorBuilder;
import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/** Entity initializer which adds an effector to an entity 
 * @since 0.7.0 */
@Beta
public class AddEffector implements EntityInitializer {
    
    public static final ConfigKey<String> EFFECTOR_NAME = ConfigKeys.newStringConfigKey("name");
    public static final ConfigKey<String> EFFECTOR_DESCRIPTION = ConfigKeys.newStringConfigKey("description");
    
    public static final ConfigKey<Map<String,Object>> EFFECTOR_PARAMETER_DEFS = new MapConfigKey<Object>(Object.class, "parameters");

    final Effector<?> effector;
    
    public AddEffector(Effector<?> effector) {
        this.effector = Preconditions.checkNotNull(effector, "effector");
    }
    
    @Override
    public void apply(EntityLocal entity) {
        ((EntityInternal)entity).getMutableEntityType().addEffector(effector);
    }
    
    public static EffectorBuilder<String> newEffectorBuilder(ConfigBag params) {
        String name = Preconditions.checkNotNull(params.get(EFFECTOR_NAME), "name must be supplied when defining an effector");
        EffectorBuilder<String> eff = Effectors.effector(String.class, name);
        eff.description(params.get(EFFECTOR_DESCRIPTION));
        
        Map<String, Object> paramDefs = params.get(EFFECTOR_PARAMETER_DEFS);
        if (paramDefs!=null) {
            for (Map.Entry<String, Object> paramDef: paramDefs.entrySet()){
                if (paramDef!=null) {
                    String paramName = paramDef.getKey();
                    Object value = paramDef.getValue();
                    if (value==null) value = Collections.emptyMap();
                    if (!(value instanceof Map)) {
                        if (value instanceof CharSequence && Strings.isBlank((CharSequence) value)) 
                            value = Collections.emptyMap();
                    }
                    if (!(value instanceof Map))
                        throw new IllegalArgumentException("Illegal argument of type "+value.getClass()+" value '"+value+"' supplied as parameter definition "
                            + "'"+paramName);
                    eff.parameter(ConfigKeys.DynamicKeys.newNamedInstance(paramName, (Map<?, ?>) value));
                }
            }
        }
        
        return eff;
    }

}
