package brooklyn.entity.webapp;


import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.lifecycle.JavaStartStopSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;

import java.io.File;
import java.util.Map;

public abstract class JavaWebAppSshDriver extends JavaStartStopSshDriver {

    public JavaWebAppSshDriver(JavaWebAppSoftwareProcess entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public JavaWebAppSoftwareProcess getEntity() {
        return (JavaWebAppSoftwareProcess) super.getEntity();
    }

    public Integer getHttpPort() {
        return entity.getAttribute(Attributes.HTTP_PORT);
    }

    @Override
    public void postLaunch() {
        assert getHttpPort() != null : "HTTP_PORT sensor not set; is an acceptable port available?";
        String rootUrl = String.format("http://%s:%s/", getHostname(), getHttpPort());
        entity.setAttribute(WebAppService.ROOT_URL, rootUrl);
    }

    protected abstract String getDeploySubdir();

    public void deploy(File file) {
        deploy(file, null);
    }

    public void deploy(File f, String targetName) {
        if (targetName == null) {
            targetName = f.getName();
        }

        String dest = getRunDir() + "/" + getDeploySubdir() + "/" + targetName;
        log.info("{} deploying {} to {}:{}", new Object[]{entity, f, getHostname(), dest});
        getMachine().run(String.format("mv -f %s %s.bak > /dev/null 2>&1", dest, dest));//back up old file/directory
        int result = getMachine().copyTo(f, dest);
        log.debug("{} deployed {} to {}:{}: result {}", new Object[]{entity, f, getHostname(), dest, result});
    }

    public void deploy(String url, String targetName) {
        String dest = getRunDir() + "/" + getDeploySubdir() + "/" + targetName;
        log.info("{} deploying {} to {}:{}", new Object[]{entity, url, getHostname(), dest});
        getMachine().run(String.format("mv -f %s %s.bak > /dev/null 2>&1", dest, dest)); //back up old file/directory
        // TODO backup not supported, is it?
        Map props = MutableMap.of("backup",true);
        int result = getMachine().copyTo(props, getResource(url), getRunDir() + "/" + getDeploySubdir() + "/" + targetName);
        log.debug("{} deployed {} to {}:{}: result {}", new Object[]{entity, url, getHostname(), dest, result});
    }
}
