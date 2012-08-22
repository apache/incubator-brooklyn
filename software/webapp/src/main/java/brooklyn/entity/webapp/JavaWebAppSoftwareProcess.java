package brooklyn.entity.webapp;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.NamedParameter;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;

import com.google.common.collect.Sets;

public abstract class JavaWebAppSoftwareProcess extends SoftwareProcessEntity implements JavaWebAppService {

    private static final Logger log = LoggerFactory.getLogger(JavaWebAppSoftwareProcess.class);

    public static final AttributeSensor<Set<String>> DEPLOYED_WARS = new BasicAttributeSensor(
            Set.class, "webapp.deployedWars", "Names of archives that are currently deployed");

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
        if (getAttribute(DEPLOYED_WARS) == null) setAttribute(DEPLOYED_WARS, Sets.<String>newLinkedHashSet());
        
        String rootWar = getConfig(ROOT_WAR);
        if (rootWar!=null) deploy(rootWar, "ROOT.war");

        List<String> namedWars = getConfig(NAMED_WARS, Collections.<String>emptyList());
        for(String war: namedWars){
            String name = war.substring(war.lastIndexOf('/') + 1);
            deploy(war, name);
        }
    }

    @Description("Deploys the given artifact")
    public void deploy(
            @NamedParameter("url") String url, 
            @NamedParameter("targetName") String targetName) {
        checkNotNull(url, "url");
        checkNotNull(targetName, "targetName");
        JavaWebAppSshDriver driver = getDriver();
        driver.deploy(url, targetName);
        
        // Update attribute
        Set<String> deployedWars = getAttribute(DEPLOYED_WARS);
        if (deployedWars == null) {
            deployedWars = Sets.newLinkedHashSet();
        }
        deployedWars.add(targetName);
        setAttribute(DEPLOYED_WARS, deployedWars);
    }

    @Description("Undeploys the given artifact")
    public void undeploy(
            @NamedParameter("targetName") String targetName) {
        JavaWebAppSshDriver driver = getDriver();
        driver.undeploy(targetName);
        
        // Update attribute
        Set<String> deployedWars = getAttribute(DEPLOYED_WARS);
        if (deployedWars == null) {
            deployedWars = Sets.newLinkedHashSet();
        }
        deployedWars.remove(targetName);
        setAttribute(DEPLOYED_WARS, deployedWars);
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
