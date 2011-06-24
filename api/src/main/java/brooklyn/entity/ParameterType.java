package brooklyn.entity;

import java.io.Serializable;

/**
 * Modeled on concepts in the JMX {@link MBeanParameterInfo} class.
 *
 * TODO javadoc
 */
public interface ParameterType<T> extends Serializable {
    /**
     * TODO javadoc
     */
    public String getName();

    /**
     * TODO javadoc
     */
    public Class<T> getParameterClass();

    /**
     * TODO javadoc
     */
    public String getParameterClassName();

    /**
     * TODO javadoc
     */
    public String getDescription();
}
