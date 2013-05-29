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
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.java.JavaAppUtils;

import com.google.common.base.Throwables;
import com.google.common.collect.Sets;

public abstract class JavaWebAppSoftwareProcessImpl extends SoftwareProcessImpl implements JavaWebAppService, JavaWebAppSoftwareProcess {

    private static final Logger log = LoggerFactory.getLogger(JavaWebAppSoftwareProcessImpl.class);

    public JavaWebAppSoftwareProcessImpl(){
        super();
    }

    public JavaWebAppSoftwareProcessImpl(Entity parent){
        this(new LinkedHashMap(),parent);
    }

    public JavaWebAppSoftwareProcessImpl(Map flags){
        this(flags, null);
    }

    public JavaWebAppSoftwareProcessImpl(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        WebAppServiceMethods.connectWebAppServerPolicies(this);
        JavaAppUtils.connectJavaAppServerPolicies(this);
    }

    //just provide better typing
    public JavaWebAppDriver getDriver() {
        return (JavaWebAppDriver) super.getDriver();
    }

    // TODO thread-safety issues: if multiple concurrent calls, may break (e.g. deployment_wars being reset)
    public void deployInitialWars() {
        if (getAttribute(DEPLOYED_WARS) == null) setAttribute(DEPLOYED_WARS, Sets.<String>newLinkedHashSet());
        
        String rootWar = getConfig(ROOT_WAR);
        if (rootWar!=null) deploy(rootWar, "ROOT.war");

        List<String> namedWars = getConfig(NAMED_WARS, Collections.<String>emptyList());
        for(String war: namedWars){
            deploy(war, getDriver().getFilenameContextMapper().findArchiveNameFromUrl(war, true));
        }
        
        Map<String,String> warsByContext = getConfig(WARS_BY_CONTEXT);
        if (warsByContext!=null) {
            for (String context: warsByContext.keySet()) {
                deploy(warsByContext.get(context), context);
            }
        }
    }

    /**
     * Deploys the given artifact, from a source URL, to a given deployment filename/context.
     * There is some variance in expected filename/context at various servers,
     * so the following conventions are followed:
     * <p>
     *   either ROOT.WAR or /       denotes root context
     * <p>
     *   anything of form  FOO.?AR  (ending .?AR) is copied with that name (unless copying not necessary)
     *                              and is expected to be served from /FOO
     * <p>
     *   anything of form  /FOO     (with leading slash) is expected to be served from /FOO
     *                              (and is copied as FOO.WAR)
     * <p>
     *   anything of form  FOO      (without a dot) is expected to be served from /FOO
     *                              (and is copied as FOO.WAR)
     * <p>                            
     *   otherwise <i>please note</i> behaviour may vary on different appservers;
     *   e.g. FOO.FOO would probably be ignored on appservers which expect a file copied across (usually),
     *   but served as /FOO.FOO on systems that take a deployment context.
     * <p>
     * See {@link FileNameToContextMappingTest} for definitive examples!
     * 
     * @param url  where to get the war, as a URL, either classpath://xxx or file:///home/xxx or http(s)...
     * @param targetName  where to tell the server to serve the WAR, see above
     */
    @Effector(description="Deploys the given artifact, from a source URL, to a given deployment filename/context")
    public void deploy(
            @EffectorParam(name="url", description="URL of WAR file") String url, 
            @EffectorParam(name="targetName", description="context path where WAR should be deployed (/ for ROOT)") String targetName) {
        try {
            checkNotNull(url, "url");
            checkNotNull(targetName, "targetName");
            JavaWebAppDriver driver = getDriver();
            String deployedName = driver.deploy(url, targetName);
            
            // Update attribute
            Set<String> deployedWars = getAttribute(DEPLOYED_WARS);
            if (deployedWars == null) {
                deployedWars = Sets.newLinkedHashSet();
            }
            deployedWars.add(deployedName);
            setAttribute(DEPLOYED_WARS, deployedWars);
        } catch (RuntimeException e) {
            // Log and propagate, so that log says which entity had problems...
            LOG.warn("Error deploying '"+url+"' to "+targetName+" on "+toString()+"; rethrowing...", e);
            throw Throwables.propagate(e);
        }
    }

    /** For the DEPLOYED_WARS to be updated, the input must match the result of the call to deploy */
    @Override
    @Effector(description="Undeploys the given context/artifact")
    public void undeploy(
            @EffectorParam(name="targetName") String targetName) {
        try {
            JavaWebAppDriver driver = getDriver();
            driver.undeploy(targetName);
            
            // Update attribute
            Set<String> deployedWars = getAttribute(DEPLOYED_WARS);
            if (deployedWars == null) {
                deployedWars = Sets.newLinkedHashSet();
            }
            deployedWars.remove(targetName);
            setAttribute(DEPLOYED_WARS, deployedWars);
        } catch (RuntimeException e) {
            // Log and propagate, so that log says which entity had problems...
            LOG.warn("Error undeploying '"+targetName+"' on "+toString()+"; rethrowing...", e);
            throw Throwables.propagate(e);
        }
    }
    
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
