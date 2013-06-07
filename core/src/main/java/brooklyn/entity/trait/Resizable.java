package brooklyn.entity.trait;


import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.MethodEffector;

/**
 * Defines an entity group that can be re-sized dynamically.
 * <p/>
 * By invoking the {@link #resize(Integer)} effector, the number of child nodes
 * can be reduced (by shutting down some of them) or increased (by provisioning new entities.)
 */
public interface Resizable {

    MethodEffector<Integer> RESIZE = new MethodEffector<Integer>(Resizable.class, "resize");

    /**
     * Grow or shrink this entity to the desired size.
     *
     * @param desiredSize the new size of the entity group.
     * @return the new size of the group.
     */
    @Effector(description="Changes the size of the entity (e.g. the number of nodes in a cluster)")
    Integer resize(@EffectorParam(name="desiredSize", description="The new size of the cluster") Integer desiredSize);

    /**
     * @return the current size of the group.
     */
    Integer getCurrentSize();
}

