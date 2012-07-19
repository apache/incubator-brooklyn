package brooklyn.entity.webapp;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class JavaWebAppSoftwareProcess extends SoftwareProcessEntity implements JavaWebAppService {

    private static final Logger log = LoggerFactory.getLogger(JavaWebAppSoftwareProcess.class);

    public JavaWebAppSoftwareProcess(){
        this(new LinkedHashMap(),null);
    }

    public JavaWebAppSoftwareProcess(Entity owner){
        this(new LinkedHashMap(),owner);
    }

    public JavaWebAppSoftwareProcess(Map flags){
        this(flags, null);
    }

    public JavaWebAppSoftwareProcess(Map flags, Entity owner) {
        super(flags, owner);
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        WebAppServiceMethods.connectWebAppServerPolicies(this);
    }

    //just provide better typing
    public JavaWebAppSshDriver getDriver() {
        return (JavaWebAppSshDriver) super.getDriver();
    }

    public void deployInitialWars() {
        String rootWar = getConfig(ROOT_WAR);
        if (rootWar!=null) getDriver().deploy(rootWar, "ROOT.war");

        JavaWebAppSshDriver driver = getDriver();
        List<String> namedWars = getConfig(NAMED_WARS, Collections.EMPTY_LIST);
        for(String war: namedWars){
            String name = war.substring(war.lastIndexOf('/') + 1);
            driver.deploy(war, name);
        }
    }

    //TODO deploy effector

    @Override
    public void stop() {
        super.stop();
        // zero our workrate derived workrates.
        // TODO might not be enough, as policy may still be executing and have a record of historic vals; should remove policies
        // (also not sure we want this; implies more generally a responsibility for sensors to announce things when disconnected,
        // vs them just showing the last known value...)
        setAttribute(REQUESTS_PER_SECOND, 0D);
        setAttribute(AVG_REQUESTS_PER_SECOND, 0D);
    }
}
