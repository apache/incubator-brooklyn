package brooklyn.entity.group;

import static com.google.common.base.Preconditions.checkNotNull;
import groovy.lang.Closure;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicGroupImpl;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.EntityFactoryForLocation;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.NamedParameter;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.proxying.WrappingEntitySpec;
import brooklyn.entity.trait.Changeable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.policy.Policy;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.MutableList;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * A cluster of entities that can dynamically increase or decrease the number of entities.
 */
@ImplementedBy(DynamicClusterImpl.class)
public interface DynamicCluster extends Entity, Group, Changeable, Cluster {

    public static final Effector<String> REPLACE_MEMBER = new MethodEffector<String>(DynamicCluster.class, "replaceMember");

    @SetFromFlag("quarantineFailedEntities")
    public static final ConfigKey<Boolean> QUARANTINE_FAILED_ENTITIES = new BasicConfigKey<Boolean>(
            Boolean.class, "dynamiccluster.quarantineFailedEntities", "Whether to guarantine entities that fail to start, or to try to clean them up", false);

    public static final BasicAttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;

    public static final BasicNotificationSensor<Entity> ENTITY_QUARANTINED = new BasicNotificationSensor<Entity>(Entity.class, "dynamiccluster.entityQuarantined", "Entity failed to start, and has been quarantined");

    public static final AttributeSensor<Group> QUARANTINE_GROUP = new BasicAttributeSensor<Group>(Group.class, "dynamiccluster.quarantineGroup", "Group of quarantined entities that failed to start");
    
    @SetFromFlag("memberSpec")
    public static final ConfigKey<EntitySpec<?>> MEMBER_SPEC = new BasicConfigKey(
            EntitySpec.class, "dynamiccluster.memberspec", "entity spec for creating new cluster members", null);

    @SetFromFlag("factory")
    public static final ConfigKey<EntityFactory> FACTORY = new BasicConfigKey<EntityFactory>(
            EntityFactory.class, "dynamiccluster.factory", "factory for creating new cluster members", null);

    @SetFromFlag("removalStrategy")
    public static final ConfigKey<Function<Collection<Entity>, Entity>> REMOVAL_STRATEGY = new BasicConfigKey(
            Function.class, "dynamiccluster.removalstrategy", "strategy for deciding what to remove when down-sizing", null);
    
    /**
     * 
     * @param memberId
     * @throws NoSuchElementException If entity cannot be resolved, or it is not a member 
     */
    @Description("Replaces the entity with the given ID, if it is a member; first adds a new member, then removes this one. "+
            "Returns id of the new entity; or throws exception if couldn't be replaced.")
    public String replaceMember(@NamedParameter("memberId") @Description("The entity id of a member to be replaced") String memberId);
    
    public void setRemovalStrategy(Function<Collection<Entity>, Entity> val);
    
    public void setRemovalStrategy(Closure val);
    
    public void setMemberSpec(EntitySpec<?> memberSpec);
    
    public void setFactory(EntityFactory<?> factory);
}
