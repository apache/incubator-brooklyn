package brooklyn.entity.webapp.jboss;

import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.webapp.JavaWebAppSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.NetworkUtils;
import com.google.common.base.Preconditions;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;


public class JBoss7SshDriver extends JavaWebAppSshDriver implements JBoss7Driver {

    /*
      * TODO
      * - security for stats access (see below)
      * - expose log file location, or even support accessing them dynamically
      * - more configurability of config files, java memory, etc
      */

    public static final String SERVER_TYPE = "standalone";
    private static final String CONFIG_FILE = "standalone-brooklyn.xml";

    public JBoss7SshDriver(JBoss7Server entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    protected String getLogFileLocation() {
        return String.format("%s/%s/log/server.log", getRunDir(), SERVER_TYPE);
    }

    protected String getDeploySubdir() {
        return String.format("%s/deployments", SERVER_TYPE);
    }

    protected Integer getManagementPort() {
        return entity.getAttribute(JBoss7Server.MANAGEMENT_PORT);
    }

    protected Integer getManagementNativePort() {
        return entity.getAttribute(JBoss7Server.MANAGEMENT_NATIVE_PORT);
    }

    protected Integer getPortIncrement() {
        return (Integer) entity.getAttribute(JBoss7Server.PORT_INCREMENT);
    }

    public void install() {
        String url = format("http://download.jboss.org/jbossas/7.1/jboss-as-%s/jboss-as-%s.tar.gz", getVersion(), getVersion());
        String saveAs = format("jboss-as-distribution-%s.tar.gz", getVersion());

        List<String> commands = new LinkedList<String>();
        commands.addAll(CommonCommands.downloadUrlAs(url, getEntityVersionLabel("/"), saveAs));
        commands.add(CommonCommands.INSTALL_TAR);
        commands.add("tar xzfv " + saveAs);

        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(commands).execute();
    }

    /**
     * AS7 config notes and TODOs:
     * We're using the http management interface on port managementPort
     * We're not using any JMX.
     * - AS 7 simply doesn't boot with Sun JMX enabled (https://issues.jboss.org/browse/JBAS-7427)
     * - 7.1 onwards uses Remoting 3, which we haven't configured
     * We're completely disabling security on the management interface.
     * - In the future we probably want to use the as7/bin/add-user.sh script using config keys for user and password
     * - Or we could create our own security realm and use that.
     * We disable the root welcome page, since we can't deploy our own root otherwise
     * We bind all interfaces to entity.hostname, rather than 127.0.0.1.
     */
    @Override
    public void customize() {
        Map ports = MutableMap.of("httpPort", getHttpPort(), "managementPort", getManagementPort(), "managementNativePort",
                getManagementNativePort());

        NetworkUtils.checkPortsValid(ports);
        String hostname = entity.getAttribute(SoftwareProcessEntity.HOSTNAME);
        Preconditions.checkNotNull(hostname, "AS 7 entity must set hostname otherwise server will only be visible on localhost");
        newScript(CUSTOMIZING).
                body.append(
                format("cp -r %s/jboss-as-%s/%s . || exit $!", getInstallDir(), getVersion(), SERVER_TYPE),
                format("cd %s/%s/configuration/", getRunDir(), SERVER_TYPE),
                format("cp standalone.xml %s", CONFIG_FILE),
                format("sed -i.bk 's/8080/%s/' %s", getHttpPort(), CONFIG_FILE),
                format("sed -i.bk 's/9990/%s/' %s", getManagementPort(), CONFIG_FILE),
                format("sed -i.bk 's/9999/%s/' %s", getManagementNativePort(), CONFIG_FILE),
                format("sed -i.bk 's/port-offset:0/port-offset:%s/' %s", getPortIncrement(), CONFIG_FILE),
                format("sed -i.bk 's/enable-welcome-root=\"true\"/enable-welcome-root=\"false\"/' %s", CONFIG_FILE),

                // Disable Management security (!) by deleting the security-realm attribute
                format("sed -i.bk 's/http-interface security-realm=\"ManagementRealm\"/http-interface/' %s", CONFIG_FILE),

                // Increase deployment timeout to ten minutes
                format("sed -i.bk 's/\\(path=\"deployments\"\\)/\\1 deployment-timeout=\"600\"/' %s", CONFIG_FILE),

                // Bind interfaces to entity hostname
                format("sed -i.bk 's/\\(inet-address value=.*\\)127.0.0.1/\\1%s/' %s", getHostname(), CONFIG_FILE)
        ).execute();

        ((JBoss7Server) entity).deployInitialWars();
    }

    @Override
    public void launch() {
        Map flags = MutableMap.of("usePidFile", false);
        newScript(flags, LAUNCHING).
                body.append(
                "export LAUNCH_JBOSS_IN_BACKGROUND=true",
                format("export JBOSS_HOME=%s/jboss-as-%s", getInstallDir(), getVersion()),
                format("export JBOSS_PIDFILE=%s/%s", getRunDir(), PID_FILENAME),
                format("%s/jboss-as-%s/bin/%s.sh ", getInstallDir(), getVersion(), SERVER_TYPE) +
                        format("--server-config %s ", CONFIG_FILE) +
                        format("-Djboss.server.base.dir=%s/%s ", getRunDir(), SERVER_TYPE) +
                        format("\"-Djboss.server.base.url=file://%s/%s\" ", getRunDir(), SERVER_TYPE) +
                        "-Djava.net.preferIPv4Stack=true " +
                        "-Djava.net.preferIPv6Addresses=false " +
                        format(" >> %s/console 2>&1 </dev/null &", getRunDir())
        ).execute();
    }


    @Override
    public boolean isRunning() {
        Map flags = MutableMap.of("usePidFile", true);
        return newScript(flags, CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        Map flags = MutableMap.of("usePidFile", true);
        newScript(flags, STOPPING).execute();
    }

    @Override
    public void kill() {
        Map flags = MutableMap.of("usePidFile", true);
        newScript(flags, KILLING).execute();
    }

    @Override
    protected List<String> getCustomJavaConfigOptions() {
        List<String> options = new LinkedList<String>();
        options.addAll(super.getCustomJavaConfigOptions());
        options.add("-Xms200m");
        options.add("-Xmx800m");
        options.add("-XX:MaxPermSize=400m");
        return options;
    }
}
