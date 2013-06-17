package brooklyn.entity.hello;

import brooklyn.config.ConfigKey;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor.IntegerAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor.StringAttributeSensor;
import brooklyn.event.basic.BasicConfigKey.StringConfigKey;
import brooklyn.event.basic.BasicSensor;

@ImplementedBy(HelloEntityImpl.class)
public interface HelloEntity extends AbstractGroup {

    /** records name of the person represented by this entity */
    public static ConfigKey<String> MY_NAME = new StringConfigKey("my.name");
    
    /** this "person"'s favourite name */
    public static AttributeSensor<String> FAVOURITE_NAME = new StringAttributeSensor("my.favourite.name");
    
    /** records age (in years) of the person represented by this entity */
    public static AttributeSensor<Integer> AGE = new IntegerAttributeSensor("my.age");
    
    /** emits a "birthday" event whenever age is changed (tests non-attribute events) */    
    public static Sensor<Void> ITS_MY_BIRTHDAY = new BasicSensor<Void>(Void.TYPE, "my.birthday");
    
    /**  */
    public static MethodEffector<Void> SET_AGE = new MethodEffector<Void>(HelloEntity.class, "setAge");
    
    @Effector(description="allows setting the age")
    public void setAge(@EffectorParam(name="age") Integer age);
}
