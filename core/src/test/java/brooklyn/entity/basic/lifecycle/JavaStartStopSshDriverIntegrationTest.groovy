package brooklyn.entity.basic.lifecycle;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*
import groovy.transform.InheritConstructors

import java.util.List

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.event.adapter.legacy.ValueProvider
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag

public class JavaStartStopSshDriverIntegrationTest {

    MachineProvisioningLocation localhost = new LocalhostMachineProvisioningLocation(name:'localhost', count:2)
    AbstractApplication app

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new AbstractApplication() {}
    }

    @AfterMethod(groups = "Integration")
    public void shutdown() {
        if (app) app.stop()
    }

    @Test(groups = "Integration")
    public void testJavaStartStopSshDriverStartsAndStopsApp() {
        MyEntity entity = new MyEntity(owner:app);
        app.start([ localhost ]);
        executeUntilSucceedsWithShutdown(entity, timeout:5000) {
            assertNotNull entity.getAttribute(SoftwareProcessEntity.SERVICE_UP)
            assertTrue entity.getAttribute(SoftwareProcessEntity.SERVICE_UP)
        }
        assertFalse entity.getAttribute(SoftwareProcessEntity.SERVICE_UP)
    }
}

@InheritConstructors
class MyEntity extends SoftwareProcessEntity {
    
    protected StartStopDriver newDriver(SshMachineLocation loc) {
        return new MyEntityDriver(this, loc);
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();

        sensorRegistry.addSensor(SoftwareProcessEntity.SERVICE_UP, { driver.isRunning() } as ValueProvider)
    }
}

class MyEntityDriver extends JavaStartStopSshDriver {

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [SoftwareProcessEntity.SUGGESTED_VERSION, "0.1"]

    public MyEntityDriver(MyEntity entity, SshMachineLocation machine) {
        super(entity, machine)
    }

    @Override
    protected String getLogFileLocation() { "${runDir}/nohup.out" }
    
    @Override
    public void install() {
        String resourceName = "/"+MyEntityApp.class.name.replace(".", "/")+".class"
        URL url = getClass().getResource(resourceName)
        if (url == null) {
            throw new IllegalStateException("Cannot find resource $resourceName")
        }
        String saveAs = "classes/"+MyEntityApp.class.getPackage().name.replace(".", "/")+"/"+MyEntityApp.class.simpleName+".class"
        newScript(INSTALLING).
            failOnNonZeroResultCode().
            body.append(
                "curl -L \"${url}\" --create-dirs -o ${saveAs} || exit 9"
            ).execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING)
            .execute();
    }
    
    @Override
    public void launch() {
        newScript(LAUNCHING, usePidFile:true)
            .body.append(
                "nohup java -classpath $installDir/classes \$JAVA_OPTS ${MyEntityApp.class.name} </dev/null &"
            ).execute();
    }
    
    @Override
    public boolean isRunning() {
        //TODO use PID instead
        newScript(CHECK_RUNNING, usePidFile:true)
            .execute() == 0;
    }
    
    @Override
    public void stop() {
        newScript(STOPPING, usePidFile:true)
            .execute();
    }

    @Override
    protected List<String> getCustomJavaConfigOptions() {
        return super.getCustomJavaConfigOptions() + ["-Dabc=def"]
    }
}
