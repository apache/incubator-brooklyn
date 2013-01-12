package brooklyn.entity.basic.lifecycle

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*
import groovy.transform.InheritConstructors

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.SoftwareProcessDriver
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.java.JavaSoftwareProcessSshDriver
import brooklyn.event.adapter.FunctionSensorAdapter
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.test.entity.TestApplicationImpl
import brooklyn.util.ResourceUtils
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.text.Identifiers

public class JavaSoftwareProcessSshDriverIntegrationTest {

    private static final long TIMEOUT_MS = 10*1000
    
    MachineProvisioningLocation localhost = new LocalhostMachineProvisioningLocation(name:'localhost')
    AbstractApplication app

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        app = new TestApplicationImpl();
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app);
    }

    @Test(groups = "Integration")
    public void testJavaStartStopSshDriverStartsAndStopsApp() {
        MyEntity entity = new MyEntity(app);
        app.start([ localhost ]);
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertTrue entity.getAttribute(SoftwareProcessEntity.SERVICE_UP)
        }
        
        entity.stop()
        assertFalse entity.getAttribute(SoftwareProcessEntity.SERVICE_UP)
    }
}

@InheritConstructors
class MyEntity extends SoftwareProcessEntity {
    
    @Override
    Class getDriverInterface() {
        return MyEntityDriver;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        sensorRegistry.register(new FunctionSensorAdapter(
            { driver.isRunning() } )).
        poll(SoftwareProcessEntity.SERVICE_UP); 
    }
}

interface MyEntityDriver extends SoftwareProcessDriver {}

class MyEntitySshDriver extends JavaSoftwareProcessSshDriver implements MyEntityDriver {

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [SoftwareProcessEntity.SUGGESTED_VERSION, "0.1"]

    public MyEntitySshDriver(MyEntity entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected String getLogFileLocation() { "${runDir}/nohup.out" }
    
    @Override
    public void install() {
        String resourceName = "/"+MyEntityApp.class.name.replace(".", "/")+".class"
        if (!new ResourceUtils(this).getResourceFromUrl(resourceName)) 
            throw new IllegalStateException("Cannot find resource $resourceName")
        String tmpFile = "/tmp/brooklyn-test-MyEntityApp-"+Identifiers.makeRandomId(6)+".class";
        int result = machine.installTo(new ResourceUtils(this), resourceName, tmpFile)
        if (result!=0) throw new IllegalStateException("Cannot install $resourceName to $tmpFile");
        String saveAs = "classes/"+MyEntityApp.class.getPackage().name.replace(".", "/")+"/"+MyEntityApp.class.simpleName+".class"
        newScript(INSTALLING).
            failOnNonZeroResultCode().
            body.append(
                "curl -L \"file://${tmpFile}\" --create-dirs -o ${saveAs} || exit 9"
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
                "nohup java -classpath $installDir/classes \$JAVA_OPTS ${MyEntityApp.class.name} &"
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
    public void kill() {
        newScript(KILLING, usePidFile:true)
            .execute();
    }

    @Override
    protected List<String> getCustomJavaConfigOptions() {
        return super.getCustomJavaConfigOptions() + ["-Dabc=def"]
    }
}
