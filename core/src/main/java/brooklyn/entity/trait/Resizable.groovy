package brooklyn.entity.trait;
import brooklyn.entity.Effector
import brooklyn.entity.basic.DefaultValue
import brooklyn.entity.basic.Description
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.basic.NamedParameter

/**
 * Defines an entity group that can be re-sized dynamically.
 *
 * By invoking the {@link #resize(Integer)} effector, the number of child nodes
 * can be reduced (by shutting down some of them) or increased (by provisioning new entities.)
 */
public interface Resizable {

    Effector<Integer> RESIZE = new MethodEffector<Integer>(Resizable.&resize)


    /**
     * Grow or shrink this entity to the desired size.
     *
     * @param desiredSize the new size of the entity group.
     * @return the new size of the group.
     */
	@Description("Changes the size of the entity (e.g. the number of nodes in a cluster)")
    Integer resize(
        @NamedParameter("desiredSize") @Description("The new size of the cluster")
        Integer desiredSize);

    /**
     * @return the current size of the group.
     */
    Integer getCurrentSize();

}

