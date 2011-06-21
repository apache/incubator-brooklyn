package brooklyn.entity;

import java.io.Serializable;

//Modeled on concepts in MBeanParameterInfo
public interface ParameterType<T> extends Serializable {
    public String getName();
    public Class<T> getParameterClass();
    public String getParameterClassName();
    public String getDescription();
}
