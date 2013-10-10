package brooklyn.entity.proxy;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(AbstractNonProvisionedControllerImpl.class)
public interface AbstractNonProvisionedController extends LoadBalancer, Entity {

    /** sensor for port to forward to on target entities */
    @SetFromFlag("portNumberSensor")
    public static final BasicAttributeSensorAndConfigKey<AttributeSensor> PORT_NUMBER_SENSOR = new BasicAttributeSensorAndConfigKey<AttributeSensor>(
            AttributeSensor.class, "member.sensor.portNumber", "Port number sensor on members (defaults to http.port)", Attributes.HTTP_PORT);

    public boolean isActive();
}
