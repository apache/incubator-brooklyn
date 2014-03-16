package brooklyn.entity.group;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.reflect.TypeToken;

@Beta
@ImplementedBy(DynamicMultiGroupImpl.class)
@SuppressWarnings("serial")
public interface DynamicMultiGroup extends DynamicGroup {

    /**
     * Implements the mapping from entity to bucket (name).
     * @see DynamicMultiGroupImpl#bucketFromAttribute(brooklyn.event.AttributeSensor)
     * @see DynamicMultiGroupImpl#bucketFromAttribute(brooklyn.event.AttributeSensor, String)
     */
    @SetFromFlag("bucketFunction")
    ConfigKey<Function<Entity, String>> BUCKET_FUNCTION = ConfigKeys.newConfigKey(
            new TypeToken<Function<Entity, String>>(){},
            "brooklyn.multigroup.bucketFunction",
            "Implements the mapping from entity to bucket (name)"
    );

    /**
     * Determines the {@link Group} type used for the "bucket" groups.
     */
    @SetFromFlag("bucketSpec")
    ConfigKey<EntitySpec<? extends Group>> BUCKET_SPEC = ConfigKeys.newConfigKey(
            new TypeToken<EntitySpec<? extends Group>>(){},
            "brooklyn.multigroup.groupSpec",
            "Determines the entity type used for the 'bucket' groups",
            EntitySpec.create(BasicGroup.class)
    );


    /**
     * Distribute entities accepted by the {@link #ENTITY_FILTER} into uniquely-named "buckets"
     * according to the {@link #BUCKET_FUNCTION}.
     * <p>
     * A {@link Group} entity is created for each required bucket and added as a managed child of
     * this component. Entities for a given bucket are added as members of the corresponding group.
     * By default, {@link BasicGroup} instances will be created for the buckets, however any group
     * entity can be used instead (e.g. with custom effectors) by specifying the relevant entity
     * spec via the {@link #BUCKET_SPEC} config key.
     * <p>
     * Entities for which the bucket function returns {@code null} are not allocated to any
     * bucket and are thus effectively excluded. Buckets that become empty following re-evaluation
     * are removed.
     *
     * @see #ENTITY_FILTER
     * @see #BUCKET_FUNCTION
     * @see #GROUP_SPEC
     */
    public void distributeEntities();

}
