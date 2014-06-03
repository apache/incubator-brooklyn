package brooklyn.entity.webapp.nodejs;

import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.WebAppServiceMethods;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public abstract class NodeJsWebAppSshDriver extends AbstractSoftwareProcessSshDriver implements NodeJsWebAppDriver {

    public NodeJsWebAppSshDriver(NodeJsWebAppSoftwareProcessImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public NodeJsWebAppSoftwareProcessImpl getEntity() {
        return (NodeJsWebAppSoftwareProcessImpl) super.getEntity();
    }

    @Override
    public Integer getHttpPort() {
        return entity.getAttribute(Attributes.HTTP_PORT);
    }

    protected String inferRootUrl() {
        return WebAppServiceMethods.inferBrooklynAccessibleRootUrl(getEntity());
    }

    @Override
    public void postLaunch() {
        String rootUrl = inferRootUrl();
        entity.setAttribute(WebAppService.ROOT_URL, rootUrl);
    }

    protected Map<String, Integer> getPortMap() {
        return ImmutableMap.of("httpPort", entity.getAttribute(WebAppService.HTTP_PORT));
    }

    @Override
    public Set<Integer> getPortsUsed() {
        return ImmutableSet.<Integer>builder()
                .addAll(super.getPortsUsed())
                .addAll(getPortMap().values())
                .build();
    }

    @Override
    public void install() {
        log.debug("Installing {}", getEntity());

        List<String> commands = ImmutableList.<String>builder()
                .add(BashCommands.installPackage(MutableMap.of("yum", "git nodejs npm"), null))
                .add(BashCommands.sudo("npm install -g n"))
                .add(BashCommands.sudo("n " + getEntity().getConfig(SoftwareProcess.SUGGESTED_VERSION)))
                .add(BashCommands.sudo("useradd -mrU " + getEntity().getConfig(NodeJsWebAppSoftwareProcess.APP_USER)))
                .build();

        newScript(INSTALLING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void customize() {
        log.debug("Customising {}", getEntity());

        String appUser = getEntity().getConfig(NodeJsWebAppSoftwareProcess.APP_USER);
        String appName = getEntity().getConfig(NodeJsWebAppService.APP_NAME);

        List<String> commands = ImmutableList.<String>builder()
                .add(String.format("git clone %s %s", getEntity().getConfig(NodeJsWebAppService.APP_GIT_REPOSITORY_URL), appName))
                .add(BashCommands.sudo(String.format("chown -R %1$s:%1$s %2$s", appUser, appName)))
                .build();

        newScript(CUSTOMIZING)
                .body.append(commands)
                .execute();
    }

    @Override
    public void launch() {
        log.debug("Launching {}", getEntity());

        String appUser = getEntity().getConfig(NodeJsWebAppSoftwareProcess.APP_USER);
        String appName = getEntity().getConfig(NodeJsWebAppService.APP_NAME);

        List<String> commands = ImmutableList.<String>builder()
                .add(String.format("cd %s", Os.mergePathsUnix(getRunDir(), appName)))
                .add(BashCommands.sudoAsUser(appUser, "nohup node " + getEntity().getConfig(NodeJsWebAppService.APP_FILE) + " &"))
                .build();

        newScript(LAUNCHING)
                .body.append(commands)
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(STOPPING).execute();
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        return MutableMap.<String, String>builder().putAll(super.getShellEnvironment())
                .put("PORT", Integer.toString(getHttpPort()))
                .build();
    }

}
