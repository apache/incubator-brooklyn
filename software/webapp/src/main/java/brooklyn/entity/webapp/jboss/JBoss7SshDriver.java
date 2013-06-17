package brooklyn.entity.webapp.jboss;

import static java.lang.String.format;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.webapp.JavaWebAppSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.NetworkUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Preconditions;

public class JBoss7SshDriver extends JavaWebAppSshDriver implements JBoss7Driver {

    /*
      * TODO
      * - security for stats access (see below)
      * - expose log file location, or even support accessing them dynamically
      * - more configurability of config files, java memory, etc
      */

    public static final String SERVER_TYPE = "standalone";
    private static final String CONFIG_FILE = "standalone-brooklyn.xml";
    private static final String KEYSTORE_FILE = ".keystore";
    
    private String expandedInstallDir;
    
    public JBoss7SshDriver(JBoss7ServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public JBoss7ServerImpl getEntity() {
        return (JBoss7ServerImpl) super.getEntity();
    }

    @Override
    public String getSslKeystoreFile() {
        return format("%s/%s/configuration/%s", getRunDir(), SERVER_TYPE, KEYSTORE_FILE);
    }
    
    protected String getTemplateConfigurationUrl() {
        return entity.getConfig(JBoss7Server.TEMPLATE_CONFIGURATION_URL);
    }

    protected String getLogFileLocation() {
        return String.format("%s/%s/log/server.log", getRunDir(), SERVER_TYPE);
    }

    protected String getDeploySubdir() {
        return String.format("%s/deployments", SERVER_TYPE);
    }

    /**
     * @deprecated since 0.5; use getManagementHttpPort() instead
     */
    private Integer getManagementPort() {
        return getManagementHttpPort();
    }

    private Integer getManagementHttpPort() {
        return entity.getAttribute(JBoss7Server.MANAGEMENT_HTTP_PORT);
    }

    private Integer getManagementHttpsPort() {
        return entity.getAttribute(JBoss7Server.MANAGEMENT_HTTPS_PORT);
    }

    private Integer getManagementNativePort() {
        return entity.getAttribute(JBoss7Server.MANAGEMENT_NATIVE_PORT);
    }

    private Integer getPortIncrement() {
        return entity.getConfig(JBoss7Server.PORT_INCREMENT);
    }

    private Integer getDeploymentTimeoutSecs() {
        return entity.getConfig(JBoss7Server.DEPLOYMENT_TIMEOUT);
    }

    private String getExpandedInstallDir() {
        if (expandedInstallDir == null) throw new IllegalStateException("expandedInstallDir is null; most likely install was not called");
        return expandedInstallDir;
    }
    
    public void install() {
        DownloadResolver resolver = entity.getManagementContext().getEntityDownloadsManager().newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        expandedInstallDir = getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("jboss-as-%s", getVersion()));
        
        List<String> commands = new LinkedList<String>();
        commands.addAll(CommonCommands.downloadUrlAs(urls, saveAs));
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
     * - We have generic support for jmxmp, which one could configure
     * We're completely disabling security on the management interface.
     * - In the future we probably want to use the as7/bin/add-user.sh script using config keys for user and password
     * - Or we could create our own security realm and use that.
     * We disable the root welcome page, since we can't deploy our own root otherwise
     * We bind all interfaces to entity.hostname, rather than 127.0.0.1.
     */
    @Override
    public void customize() {
        // Check that ports are all configured
        Map<String,Integer> ports = MutableMap.<String,Integer>builder()
                .put("managementHttpPort", getManagementHttpPort()) 
                .put("managementHttpsPort", getManagementHttpsPort())
                .put("managementNativePort", getManagementNativePort())
                .build();
        if (isProtocolEnabled("HTTP")) {
            ports.put("httpPort", getHttpPort());
        }
        if (isProtocolEnabled("HTTPS")) {
            ports.put("httpsPort", getHttpsPort());
        }
        NetworkUtils.checkPortsValid(ports);

        // Check hostname is defined
        String hostname = entity.getAttribute(SoftwareProcess.HOSTNAME);
        Preconditions.checkNotNull(hostname, "AS 7 entity must set hostname otherwise server will only be visible on localhost");
        
        // Copy the install files to the run-dir
        newScript(CUSTOMIZING)
                .body.append(format("cp -r %s/%s . || exit $!", getExpandedInstallDir(), SERVER_TYPE))
                .execute();

        // Copy the keystore across, if there is one
        if (isProtocolEnabled("HTTPS")) {
            String keystoreUrl = getSslKeystoreUrl();
            if (keystoreUrl == null) {
                throw new NullPointerException("keystore URL must be specified if using HTTPS for "+entity);
            }
            String destinationSslKeystoreFile = getSslKeystoreFile();
            InputStream keystoreStream = new ResourceUtils(this).getResourceFromUrl(keystoreUrl);
            getMachine().copyTo(keystoreStream, destinationSslKeystoreFile);
        }

        // Copy the configuration file across
        String configFileContents = processTemplate(getTemplateConfigurationUrl());
        String destinationConfigFile = format("%s/%s/configuration/%s", getRunDir(), SERVER_TYPE, CONFIG_FILE);
        getMachine().copyTo(new ByteArrayInputStream(configFileContents.getBytes()), destinationConfigFile);
        
        // Copy the initial wars to the deploys directory
        getEntity().deployInitialWars();
    }

    @Override
    public void launch() {
        Map flags = MutableMap.of("usePidFile", false);
        
        // We wait for evidence of tomcat running because, using 
        // brooklyn.ssh.config.tool.class=brooklyn.util.internal.ssh.cli.SshCliTool,
        // we saw the ssh session return before the tomcat process was fully running 
        // so the process failed to start.
        newScript(flags, LAUNCHING).
                body.append(
                "export LAUNCH_JBOSS_IN_BACKGROUND=true",
                format("export JBOSS_HOME=%s", getExpandedInstallDir()),
                format("export JBOSS_PIDFILE=%s/%s", getRunDir(), PID_FILENAME),
                format("%s/bin/%s.sh ", getExpandedInstallDir(), SERVER_TYPE) +
                        format("--server-config %s ", CONFIG_FILE) +
                        format("-Djboss.server.base.dir=%s/%s ", getRunDir(), SERVER_TYPE) +
                        format("\"-Djboss.server.base.url=file://%s/%s\" ", getRunDir(), SERVER_TYPE) +
                        "-Djava.net.preferIPv4Stack=true " +
                        "-Djava.net.preferIPv6Addresses=false " +
                        format(" >> %s/console 2>&1 </dev/null &", getRunDir()),
                "for i in {1..10}\n" +
                        "do\n" +
                        "    grep -i 'starting' "+getRunDir()+"/console && exit\n" +
                        "    sleep 1\n" +
                        "done\n" +
                        "echo \"Couldn't determine if process is running (console output does not contain 'starting'); continuing but may subsequently fail\""
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
