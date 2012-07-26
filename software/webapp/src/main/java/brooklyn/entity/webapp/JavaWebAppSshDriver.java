package brooklyn.entity.webapp;


import java.io.File;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.lifecycle.JavaStartStopSshDriver;
import brooklyn.location.basic.SshMachineLocation;

public abstract class JavaWebAppSshDriver extends JavaStartStopSshDriver implements JavaWebAppDriver {

    public JavaWebAppSshDriver(JavaWebAppSoftwareProcess entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public JavaWebAppSoftwareProcess getEntity() {
        return (JavaWebAppSoftwareProcess) super.getEntity();
    }

    @Override
    public Integer getHttpPort() {
        return entity.getAttribute(Attributes.HTTP_PORT);
    }

    @Override
    public void postLaunch() {
        assert getHttpPort() != null : "HTTP_PORT sensor not set; is an acceptable port available?";
        String rootUrl = String.format("http://%s:%s/", getHostname(), getHttpPort());
        entity.setAttribute(WebAppService.ROOT_URL, rootUrl);
    }

    /** 
     * if files should be placed on the server for deployment,
     * override this to be the sub-directory of the runDir where they should be stored
     * (or override getDeployDir() if they should be copied somewhere else,
     * and set this null);
     * if files are not copied to the server, but injected (e.g. JMX or uploaded)
     * then override {@link #deploy(String, String)} as appropriate,
     * using getContextFromDeploymentTargetName(targetName)
     * and override this to return null
     */
    protected abstract String getDeploySubdir();
    
    protected String getDeployDir() {
        if (getDeploySubdir()==null)
            throw new IllegalStateException("no deployment directory available for "+this);
        return getRunDir() + "/" + getDeploySubdir();
    }

    @Override
    public void deploy(File file) {
        deploy(file, null);
    }

    @Override
    public void deploy(File f, String targetName) {
        if (targetName == null) {
            targetName = f.getName();
        }
        deploy("file://"+f.getAbsolutePath(), targetName);
    }

    /** deploys a URL as a webapp at the appserver;
     * returns a token which can be used as an argument to undeploy,
     * typically the web application archive (eg WAR, EAR, JAR) filename (if copying files)
     * or the web context (if using an API to manage).
     * <p>
     * see {@link JavaWebAppSoftwareProcess#deploy(String, String)} for details of how input filenames are handled */
    @Override
    public String deploy(String url, String targetName) {
        String canonicalTargetName = getFilenameContextMapper().convertDeploymentTargetNameToFilename(targetName);
        String dest = getDeployDir() + "/" + canonicalTargetName;
        log.info("{} deploying {} to {}:{}", new Object[]{entity, url, getHostname(), dest});
        // create a backup
        getMachine().run(String.format("mv -f %s %s.bak > /dev/null 2>&1", dest, dest)); //back up old file/directory
        // TODO we could try resolving http resources remotely, rather than fetching here then copying
        int result = getMachine().copyTo(getResource(url), dest);
        log.debug("{} deployed {} to {}:{}: result {}", new Object[]{entity, url, getHostname(), dest, result});
        return getFilenameContextMapper().convertDeploymentTargetNameToContext(canonicalTargetName);
    }
    
    public void undeploy(String targetName) {
        String dest = getDeployDir() + "/" + getFilenameContextMapper().convertDeploymentTargetNameToFilename(targetName);
        log.info("{} undeploying {}:{}", new Object[]{entity, getHostname(), dest});
        int result = getMachine().run(String.format("rm -f %s", dest));
        log.debug("{} undeployed {}:{}: result {}", new Object[]{entity, getHostname(), dest, result});
    }
    
    protected FilenameToWebContextMapper getFilenameContextMapper() {
        return new FilenameToWebContextMapper();
    }
}
