package brooklyn.entity;

import java.io.Serializable;
import java.util.List;

import javax.management.MBeanOperationInfo;

/**
 * An operation of some kind, carried out by an {@link Entity}.
 *
 * Modeled on concepts in the JMX {@link MBeanOperationInfo} class.
 * <p>
 */
public interface Effector<T> extends Serializable {
    /**
     * human-friendly name of the effector (although frequently this uses java method naming convention)
     */
	String getName();

    Class<T> getReturnType();

    /**
     * canonical name of return type (in case return type does not resolve after serialization)
     */
    String getReturnTypeName();

    /**
     * parameters expected by method, including name and type, optional description and default value
     */
	List<ParameterType<?>> getParameters();

    /**
     * optional description for the effector
     */
    String getDescription();

}
