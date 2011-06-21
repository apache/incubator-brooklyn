package brooklyn.entity;

import java.io.Serializable;
import java.util.List;

//Modeled on concepts in MBeanOperationInfo
public interface Effector extends Serializable {

	public String getName();
    public String getReturnType();
    public List<ParameterType> getParameters();
    public String getDescription();

}
