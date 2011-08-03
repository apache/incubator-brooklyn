package brooklyn.entity.webapp.jboss

import groovy.lang.MetaClass
import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.event.adapter.HttpSensorAdapter
import brooklyn.event.adapter.ValueProvider
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

class JBoss7Server extends JavaWebApp {

    public static final ConfiguredAttributeSensor<Integer> HTTP_PORT = Attributes.HTTP_PORT
    public static final ConfiguredAttributeSensor<Integer> MANAGEMENT_PORT = 
            [ Integer, "http.managementPort", "Management port", 9990 ]
            
    public static final BasicAttributeSensor<Integer> MANAGEMENT_STATUS =
            [ Integer, "webapp.http.managementStatus", "HTTP response code for the management server" ]
    
    public JBoss7Server(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }
    
    @Override
    protected void waitForHttpPort() {
        // TODO Auto-generated method stub
    }

    @Override
    protected void initSensors() {
        def host = getAttribute(JMX_HOST)
        def port = getAttribute(MANAGEMENT_PORT)
        
        httpAdapter = new HttpSensorAdapter(this)
        attributePoller.addSensor(MANAGEMENT_STATUS, httpAdapter.newStatusValueProvider("http://$host:$port/management/"))
        attributePoller.addSensor(SERVICE_UP, { getAttribute(MANAGEMENT_STATUS) == 200 } as ValueProvider<Boolean>)
    }

    @Override
    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return JBoss7SshSetup.newInstance(this, machine)
    }

}
