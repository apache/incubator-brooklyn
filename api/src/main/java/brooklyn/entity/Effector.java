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
	public String getName();

    /**
     * TODO javadoc
     */
    public Class<T> getReturnType();

    /**
     * TODO javadoc
     */
    public String getReturnTypeName();

    /**
     * TODO javadoc
     */
	public List<ParameterType<?>> getParameters();

    /**
     * TODO javadoc
     */
    public String getDescription();

    /**
     * TODO javadoc
     */
	public T call(Entity entity, Map<?, ?> parameters);
}
