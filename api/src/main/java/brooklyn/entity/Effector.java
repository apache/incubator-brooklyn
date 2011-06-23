package brooklyn.entity;

import java.io.Serializable;
import java.util.List;

//Modeled on concepts in MBeanOperationInfo
public interface Effector<T> extends Serializable {

	public String getName();
    public Class<T> getReturnType();
    public String getReturnTypeName();
    @SuppressWarnings("rawtypes")
	public List<ParameterType> getParameters();
    public String getDescription();

}
