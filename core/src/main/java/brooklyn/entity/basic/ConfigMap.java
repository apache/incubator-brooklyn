package brooklyn.entity.basic;

import java.util.Map;

import brooklyn.entity.ConfigKey;

/** @deprecated since 0.4.0 use brooklyn.entity.ConfigMap as interface, or appropriate implementation where needed */
public interface ConfigMap extends brooklyn.entity.ConfigMap {

    public Object setConfig(ConfigKey<?> key, Object v);
    public void setInheritedConfig(Map<ConfigKey<?>, ? extends Object> vals);
    public void clearInheritedConfig();
    
}
