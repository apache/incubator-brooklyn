package brooklyn.entity.nosql.mongodb.sharding;

import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SameServerEntity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.reflect.TypeToken;

public interface CoLocatedMongoDBRouter extends SameServerEntity {
    @SuppressWarnings("serial")
    @SetFromFlag("siblingSpecs")
    ConfigKey<Set<EntitySpec<?>>> SIBLING_SPECS = ConfigKeys.newConfigKey(new TypeToken<Set<EntitySpec<?>>>(){}, 
            "mongodb.colocatedrouter.sibling.specs", "Set of (configured) specs for entities to be co-located with the router");
}
