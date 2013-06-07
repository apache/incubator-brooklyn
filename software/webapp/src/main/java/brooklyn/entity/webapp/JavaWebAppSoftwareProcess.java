package brooklyn.entity.webapp;

import java.util.Set;

import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;

public interface JavaWebAppSoftwareProcess extends SoftwareProcess, JavaWebAppService {

    public static final AttributeSensor<Set<String>> DEPLOYED_WARS = new BasicAttributeSensor(
            Set.class, "webapp.deployedWars", "Names of archives/contexts that are currently deployed");

    public static final MethodEffector<Void> DEPLOY = new MethodEffector<Void>(JavaWebAppSoftwareProcess.class, "deploy");
    public static final MethodEffector<Void> UNDEPLOY = new MethodEffector<Void>(JavaWebAppSoftwareProcess.class, "undeploy");

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
            @EffectorParam(name="targetName", description="context path where WAR should be deployed (/ for ROOT)") String targetName);

    /** 
     * For the DEPLOYED_WARS to be updated, the input must match the result of the call to deploy
     */
    @Effector(description="Undeploys the given context/artifact")
    public void undeploy(
            @EffectorParam(name="targetName") String targetName);
}
