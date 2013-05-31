package brooklyn.entity;

import java.io.Serializable;

import javax.management.MBeanParameterInfo;

/**
 * Similar to the concepts in the JMX {@link MBeanParameterInfo} class.
 *
 * @see Effector
 */
public interface ParameterType<T> extends Serializable {
    
    public String getName();

    public Class<T> getParameterClass();

    /**
     * The canonical name of the parameter class; especially useful if the class 
     * cannot be resolved after deserialization. 
     */
    public String getParameterClassName();

    public String getDescription();

    /**
     * @return The default value for this parameter, if not supplied during an effector call.
     */
    public T getDefaultValue();
}
