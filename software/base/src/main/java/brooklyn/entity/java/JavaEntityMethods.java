package brooklyn.entity.java;

import brooklyn.entity.ConfigKey;

/** DSL conveniences for Java entities. Also see {@link JavaAppUtils} for methods useful within Java classes. */
public class JavaEntityMethods {

    public static ConfigKey<String> javaSysProp(String propertyName) { return UsesJava.JAVA_SYSPROPS.subKey(propertyName); }
    
    // TODO javaMaxHeap javaInitialHeap javaMaxPermGen should all be supplied as ListConfigs on JAVA_OPTIONS
    
}
