package brooklyn.entity.webapp.jboss;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.webapp.JavaWebAppSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.ssh.CommonCommands;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.lang.String.format;

public class JBoss7SshDriver extends JavaWebAppSshDriver implements JBoss7Driver {

    public static final Logger LOG = LoggerFactory.getLogger(JBoss7SshDriver.class);

    /*
      * TODO
      * - expose log file location, or even support accessing them dynamically
      * - more configurability of config files, java memory, etc
      */

    public static final String SERVER_TYPE = "standalone";
    private static final String CONFIG_FILE = "standalone-brooklyn.xml";
    private static final String KEYSTORE_FILE = ".keystore";

    private static final String MANAGEMENT_REALM = "ManagementRealm";

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

    private String getManagementUsername() {
        return entity.getConfig(JBoss7Server.MANAGEMENT_USER);
    }

    private String getManagementPassword() {
        return entity.getConfig(JBoss7Server.MANAGEMENT_PASSWORD);
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
        // Check that a password was set for the management user
        Preconditions.checkState(!Strings.isNullOrEmpty(getManagementUsername()), "User for management realm required");
        String managementPassword = getManagementPassword();
        if (Strings.isNullOrEmpty(managementPassword)) {
            LOG.debug(this+" has no password specified for "+JBoss7Server.MANAGEMENT_PASSWORD.getName()+"; using a random string");
            entity.setConfig(JBoss7Server.MANAGEMENT_PASSWORD, UUID.randomUUID().toString());
        }
        String hashedPassword = hashPassword(getManagementUsername(), getManagementPassword(), MANAGEMENT_REALM);

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
        Networking.checkPortsValid(ports);

        // Check hostname is defined
        String hostname = entity.getAttribute(SoftwareProcess.HOSTNAME);
        Preconditions.checkNotNull(hostname, "AS 7 entity must set hostname otherwise server will only be visible on localhost");
        
        // Copy the install files to the run-dir and add the management user
        newScript(CUSTOMIZING)
                .body.append(
                    format("cp -r %s/%s . || exit $!", getExpandedInstallDir(), SERVER_TYPE),
                    format("echo -e '\n%s=%s' >> %s/%s/configuration/mgmt-users.properties",
                            getManagementUsername(), hashedPassword, getRunDir(), SERVER_TYPE)
                ).execute();

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

    /**
     * Creates a hash of a username, password and security realm that is suitable for use
     * with AS7 and Wildfire.
     * <p/>
     * Although AS7 has an <code>add-user.sh</code> script it is unsuitable for use in
     * non-interactive modes. (See AS7-5061 for details.) Versions 7.1.2+ (EAP) accept
     * a <code>--silent</code> flag. When this entity is updated past 7.1.1 we should
     * probably use that instead.
     * <p/>
     * This method mirrors AS7 and Wildfire's method of hashing user's passwords. Refer
     * to its class <code>UsernamePasswordHashUtil.generateHashedURP</code> for their
     * implementation.
     *
     * @see <a href="https://issues.jboss.org/browse/AS7-5061">AS7-5061</a>
     * @see <a href="https://github.com/jboss-remoting/jboss-sasl/blob/master/src/main/java/org/jboss/sasl/util/UsernamePasswordHashUtil.java">
     *     UsernamePasswordHashUtil.generateHashedURP</a>
     * @return <code>HEX(MD5(username ':' realm ':' password))</code>
     */
    public static String hashPassword(String username, String password, String realm) {
        String concat = username + ":" + realm + ":" + password;
        byte[] hashed = Hashing.md5().hashString(concat, Charsets.UTF_8).asBytes();
        return BaseEncoding.base16().lowerCase().encode(hashed);
    }
}
