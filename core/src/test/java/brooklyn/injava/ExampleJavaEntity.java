package brooklyn.injava;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BasicParameterType;
import brooklyn.entity.basic.ExplicitEffector;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * A toy entity, for testing that a pure-java implementation works. 
 */
public class ExampleJavaEntity extends AbstractEntity {
    private static final long serialVersionUID = -3526068165423444054L;

    @SetFromFlag("myConfig1")
    public static final BasicConfigKey<String>  MY_CONFIG1 = new BasicConfigKey<String>(String.class, "example.java.myConfig1", "My config 1", "default val1");

    public static final BasicAttributeSensor<String>  MY_SENSOR1 = new BasicAttributeSensor<String>(String.class, "example.java.mySensor1", "My sensor 1");

    @SuppressWarnings("serial")
    public static final Effector<Void> EFFECTOR1 = new ExplicitEffector<ExampleJavaEntity, Void>(
            "effector1", 
            Void.TYPE, 
            Arrays.<ParameterType<?>>asList(new BasicParameterType<String>("arg0", String.class, "Arg 0")),
            "Invoke effector 1") {
        @Override public Void invokeEffector(ExampleJavaEntity entity, Map<String,?> m) {
            entity.effector1((String) m.get("arg0"));
            return null;
        }
    };

    final List<Object> effectorInvocations = new CopyOnWriteArrayList<Object>();

    public ExampleJavaEntity() {
        super();
    }

    public ExampleJavaEntity(Map<String,?> flags, Entity parent) {
        super(flags, parent);
    }

    public ExampleJavaEntity(Entity parent) {
        super(parent);
    }
    
    public void effector1(String arg1) {
        effectorInvocations.add(arg1);
    }
}