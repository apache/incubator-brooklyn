package brooklyn.entity.trait;

import java.util.Arrays;
import java.util.Map;

import brooklyn.entity.Effector;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.EffectorInferredFromAnnotatedMethod;
import brooklyn.entity.basic.NamedParameter;

/**
 * Defines an entity group that can be re-sized dynamically. By invoking the @{link #resize} effector, the number of child nodes
 * can be reduced (by shutting down some of them) or increased (by provisioning new entities.)
 */
public interface Resizable {
    Effector<Integer> RESIZE = new EffectorInferredFromAnnotatedMethod<Integer>(Resizable.class, "resize",
            "Changes the size of the entity (e.g. the number of nodes in a cluster)");

    /**
     * Grow or shrink this entity to the desired size.
     *
     * @param desiredSize the new size of the entity group.
     * @return a ResizeResult object that describes the outcome of the action.
     */
    Integer resize(@NamedParameter("desiredSize")
                   @Description("The new size of the cluster") int desiredSize);
}

