package brooklyn.entity.trait;

import java.util.Arrays;
import java.util.Map;

import brooklyn.entity.Effector;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.BasicParameterType;
import brooklyn.entity.basic.EffectorWithExplicitImplementation;

/**
 * Defines an entity group that can be re-sized dynamically. By invoking the @{link #resize} effector, the number of child nodes
 * can be reduced (by shutting down some of them) or increased (by provisioning new entities.)
 */
public interface Resizable {

    public static final Effector<ResizeResult> RESIZE = new EffectorWithExplicitImplementation<Resizable,ResizeResult>(
            "resize", ResizeResult.class, 
            Arrays.<ParameterType<?>>asList(
                new BasicParameterType<Integer>("desiredSize", Integer.class, "the desired new size of the cluster")
            ), "Changes the size of the entity (e.g. the number of nodes in a cluster)") {
        
        private static final long serialVersionUID = 1L;
        
        public ResizeResult invokeEffector(Resizable r, @SuppressWarnings("rawtypes") Map params) {
              r.resize((Integer) params.get("desiredSize"));
              return null;
        }
    };
    
    /**
     * Grow or shrink this entity to the desired size.
     * @param desiredSize the new size of the entity group.
     * @return a ResizeResult object that describes the outcome of the action.
     */
    ResizeResult resize(int desiredSize);
}

