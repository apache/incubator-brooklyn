package brooklyn.entity.hello;

import static org.testng.Assert.*
import brooklyn.entity.Effector
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.EffectorInferredFromAnnotatedMethod
import brooklyn.entity.basic.NamedParameter
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.BasicSensor
import brooklyn.event.basic.ConfigKey


public class HelloEntity extends AbstractGroup {
    public HelloEntity(Map flags=[:]) { super(flags) }

    /** records name of the person represented by this entity */
    public static ConfigKey<String> MY_NAME = new BasicConfigKey<String>(String.class, "my.name");
    
    /** this "person"'s favourite name */
    public static Sensor<String> FAVOURITE_NAME = new BasicAttributeSensor<String>(String.class, "my.favourite.name");
    
    /** records age (in years) of the person represented by this entity */
    public static Sensor<Integer> AGE = new BasicAttributeSensor<Integer>(Integer.class, "my.age");
    
    /** emits a "birthday" event whenever age is changed (tests non-attribute events) */    
    public static Sensor<Void> ITS_MY_BIRTHDAY = new BasicSensor<Void>(Void.TYPE, "my.birthday");
    
    /**  */
    public static Effector<Void> SET_AGE = new EffectorInferredFromAnnotatedMethod<String>(HelloEntity.class, "setAge", "allows setting the age");
    
    public void setAge(@NamedParameter("age") Integer age) {
        updateAttribute(AGE, age);
        emit(ITS_MY_BIRTHDAY, null);
    }
}
