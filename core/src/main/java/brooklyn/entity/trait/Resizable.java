package brooklyn.entity.trait;

import brooklyn.entity.Effector;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.EffectorWithExplicitImplementation;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Defines an entity group that can be re-sized dynamically. By invoking the @{link #resize} effector, the number of child nodes
 * can be reduced (by shutting down some of them) or increased (by provisioning new entities.)
 */
public interface Resizable {

    Effector<ResizeResult> RESIZE = new EffectorWithExplicitImplementation<Resizable,ResizeResult>("resize", ResizeResult.class,
        Arrays.<ParameterType<?>>asList(new ParameterType<Collection<?>>() {
            public String getName() { return "desiredSize"; }
            public Class getParameterClass() { return Integer.class; }
            public String getParameterClassName() { return null; }
            public String getDescription() { return "the new size of the cluster"; }
        }),
        "change the number of nodes in the cluster") {
        public ResizeResult invokeEffector(Resizable r, Map params) {
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

