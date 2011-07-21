package brooklyn.entity.trait;

import java.util.Arrays;
import java.util.Map;

import brooklyn.entity.Effector;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.BasicParameterType;
import brooklyn.entity.basic.EffectorWithExplicitImplementation;

/**
 * Defines an entity group that can be re-sized dynamically.
 *
 * By invoking the {@link #resize(Integer)} effector, the number of child nodes
 * can be reduced (by shutting down some of them) or increased (by provisioning new entities.)
 */
public interface Resizable {
    @SuppressWarnings({ "rawtypes" })
    Effector<Integer> RESIZE = new EffectorWithExplicitImplementation<Resizable, Integer>("resize", Integer.class, 
            Arrays.<ParameterType<?>>asList(new BasicParameterType<Integer>("desiredSize", Integer.class, "The new size of the cluster", Integer.valueOf(0))),
            "Changes the size of the entity (e.g. the number of nodes in a cluster)") {
        /** serialVersionUID */
        private static final long serialVersionUID = -2254737089829684645L;
        public Integer invokeEffector(Resizable entity, Map m) {
            return entity.resize((Integer) m.get("desiredSize"));
        }
    };

    /**
     * Grow or shrink this entity to the desired size.
     *
     * @param desiredSize the new size of the entity group.
     * @return the new size of the group.
     */
    Integer resize(Integer desiredSize);
}

