package brooklyn.entity.basic.lifecycle;

import java.util.List;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcessDriver;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.text.Identifiers;

public class MyEntityImpl extends SoftwareProcessImpl implements MyEntity {
    @Override
    public Class getDriverInterface() {
        return MyEntityDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }
    
    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
    }
    
    public interface MyEntityDriver extends SoftwareProcessDriver {}

    public static class MyEntitySshDriver extends JavaSoftwareProcessSshDriver implements MyEntityDriver {

        @SetFromFlag("version")
        public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.1");

        public MyEntitySshDriver(MyEntityImpl entity, SshMachineLocation machine) {
            super(entity, machine);
        }

        @Override
        protected String getLogFileLocation() {
            return getRunDir()+"/nohup.out";
        }
        
        @Override
        public void install() {
            String resourceName = "/"+MyEntityApp.class.getName().replace(".", "/")+".class";
            if (new ResourceUtils(this).getResourceFromUrl(resourceName) == null) 
                throw new IllegalStateException("Cannot find resource "+resourceName);
            String tmpFile = "/tmp/brooklyn-test-MyEntityApp-"+Identifiers.makeRandomId(6)+".class";
            int result = getMachine().installTo(new ResourceUtils(this), resourceName, tmpFile);
            if (result!=0) throw new IllegalStateException("Cannot install "+resourceName+" to "+tmpFile);
            String saveAs = "classes/"+MyEntityApp.class.getPackage().getName().replace(".", "/")+"/"+MyEntityApp.class.getSimpleName()+".class";
            newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(
                    "curl -L \"file://"+tmpFile+"\" --create-dirs -o "+saveAs+" || exit 9"
                ).execute();
        }

        @Override
        public void customize() {
            newScript(CUSTOMIZING)
                .execute();
        }
        
        @Override
        public void launch() {
            newScript(MutableMap.of("usePidFile", true), LAUNCHING)
                .body.append(
                    String.format("nohup java -classpath %s/classes $JAVA_OPTS %s &", getInstallDir(), MyEntityApp.class.getName())
                ).execute();
        }
        
        @Override
        public boolean isRunning() {
            //TODO use PID instead
            return newScript(MutableMap.of("usePidFile", true), CHECK_RUNNING)
                .execute() == 0;
        }
        
        @Override
        public void stop() {
            newScript(MutableMap.of("usePidFile", true), STOPPING)
                .execute();
        }

        @Override
        public void kill() {
            newScript(MutableMap.of("usePidFile", true), KILLING)
                .execute();
        }

        @Override
        protected List<String> getCustomJavaConfigOptions() {
            return MutableList.<String>builder().addAll(super.getCustomJavaConfigOptions()).add("-Dabc=def").build();
        }
    }
}
