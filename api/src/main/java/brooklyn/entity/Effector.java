package brooklyn.entity;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * An operation of some kind, carried out by an {@link Entity}.
 *
 * Modeled on concepts in the JMX {@link MBeanOperationInfo} class.
 * <p>
 * TODO ENGR-1560 javadoc
 */
public interface Effector<T> extends Serializable {
    /**
     * TODO javadoc
     */
	String getName();

    /**
     * TODO javadoc
     */
    Class<T> getReturnType();

    /**
     * TODO javadoc
     */
    String getReturnTypeName();

    /**
     * TODO javadoc
     */
	List<ParameterType<?>> getParameters();

    /**
     * TODO javadoc
     */
    String getDescription();

    /**
     * TODO javadoc
     */
	T call(Entity entity, Map parameters);
}
