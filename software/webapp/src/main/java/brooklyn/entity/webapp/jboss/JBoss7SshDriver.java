package brooklyn.entity.webapp.jboss;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.basic.lifecycle.CommonCommands;
import brooklyn.entity.webapp.JavaWebAppSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.NetworkUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;


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

    protected String getTemplateConfigurationUrl() {
        return entity.getAttribute(JBoss7Server.TEMPLATE_CONFIGURATION_URL);
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
    protected Integer getManagementPort() {
        return getManagementHttpPort();
    }

    protected Integer getManagementHttpPort() {
        return entity.getAttribute(JBoss7Server.MANAGEMENT_HTTP_PORT);
    }

    protected Integer getManagementHttpsPort() {
        return entity.getAttribute(JBoss7Server.MANAGEMENT_HTTPS_PORT);
    }

    protected Integer getManagementNativePort() {
        return entity.getAttribute(JBoss7Server.MANAGEMENT_NATIVE_PORT);
    }

    protected Integer getPortIncrement() {
        return entity.getAttribute(JBoss7Server.PORT_INCREMENT);
    }

    private Integer getDeploymentTimeoutSecs() {
        return entity.getAttribute(JBoss7Server.DEPLOYMENT_TIMEOUT);
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
        String hostname = entity.getAttribute(SoftwareProcessEntity.HOSTNAME);
        Preconditions.checkNotNull(hostname, "AS 7 entity must set hostname otherwise server will only be visible on localhost");
        
        // Copy the install files to the run-dir
        newScript(CUSTOMIZING)
                .body.append(format("cp -r %s/jboss-as-%s/%s . || exit $!", getInstallDir(), getVersion(), SERVER_TYPE))
                .execute();

        // Copy the keystore across, if there is one
        String destinationKeystorePath = getRunDir()+"/"+".keystore";
        if (isProtocolEnabled("HTTPS")) {
            String keystoreUrl = getSslKeystoreUrl();
            if (keystoreUrl == null) {
                throw new NullPointerException("keystore URL must be specified if using HTTPS for "+entity);
            }
            InputStream keystoreStream = new ResourceUtils(this).getResourceFromUrl(keystoreUrl);
            getMachine().copyTo(keystoreStream, destinationKeystorePath);
        }

        // Copy the configuration file across
        String configFileContents = getConfigFileContents(getTemplateConfigurationUrl(), destinationKeystorePath);
        String destinationConfigFile = format("%s/%s/configuration/%s", getRunDir(), SERVER_TYPE, CONFIG_FILE);
        getMachine().copyTo(new ByteArrayInputStream(configFileContents.getBytes()), destinationConfigFile);
        
        // Copy the initial wars to the deploys directory
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

    // Prepare the configuration file (from the template)
    protected String getConfigFileContents(String templateConfigUrl, String destinationKeystorePath) {
        Map<String,String> substitutions = Maps.newLinkedHashMap();
        
        substitutions.put("managementHttpsPort", "${jboss.management.https.port:"+getManagementHttpsPort()+"}");
        substitutions.put("managementHttpPort", "${jboss.management.http.port:"+getManagementHttpPort()+"}");
        substitutions.put("managementNativePort", "${jboss.management.native.port:"+getManagementNativePort()+"}");
        substitutions.put("portOffset", "${jboss.socket.binding.port-offset:"+getPortIncrement()+"}");
        substitutions.put("welcomeRootEnabled", ""+false);

        substitutions.put("jbossServerConfigDir", "${jboss.server.config.dir}");

        // Bind interfaces -- to all (does this work?)
        substitutions.put("jbossBindAddress", "${jboss.bind.address:"+entity.getConfig(JBoss7Server.BIND_ADDRESS)+"}");
        substitutions.put("jbossBindAddressManagement", "${jboss.bind.address.management:"+entity.getConfig(JBoss7Server.BIND_ADDRESS)+"}");
        substitutions.put("jbossBindAddressUnsecure", "${jboss.bind.address.unsecure:"+entity.getConfig(JBoss7Server.BIND_ADDRESS)+"}");
        
        // Disable Management security (!) by excluding the security-realm attribute
        substitutions.put("httpManagementInterfaceSecurityRealm", "");

        substitutions.put("deploymentTimeout", ""+getDeploymentTimeoutSecs());

        if (isProtocolEnabled("HTTP")) {
            substitutions.put("httpEnabled", ""+true);
            substitutions.put("httpPort", ""+getHttpPort());
        } else {
            substitutions.put("httpEnabled", ""+false);
            substitutions.put("httpPort", ""+8080);
        }

        if (isProtocolEnabled("HTTPS")) {
            substitutions.put("httpsEnabled", ""+true);
            substitutions.put("httpsPort", ""+getHttpsPort());
            substitutions.put("sslKeyAlias", getSslKeyAlias());
            substitutions.put("sslKeystorePassword", getSslKeystorePassword());
            substitutions.put("sslKeystorePath", checkNotNull(destinationKeystorePath, "destinationKeystorePath"));
        } else {
            substitutions.put("httpsEnabled", ""+false);
            substitutions.put("httpsPort", ""+8443);
            substitutions.put("sslKeyAlias", "none");
            substitutions.put("sslKeystorePassword", "none");
            substitutions.put("sslKeystorePath", "none");
        }

        return processTemplate(getTemplateConfigurationUrl(), substitutions);
    }

    private String processTemplate(String url, Map<String,String> substitutions) {
        try {
            String templateConfigFile = new ResourceUtils(this).getResourceAsString(url);
            
            Configuration cfg = new Configuration();
            StringTemplateLoader templateLoader = new StringTemplateLoader();
            templateLoader.putTemplate("config", templateConfigFile);
            cfg.setTemplateLoader(templateLoader);
            Template template = cfg.getTemplate("config");
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Writer out = new OutputStreamWriter(baos);
            template.process(substitutions, out);
            out.flush();
            
            return new String(baos.toByteArray());
        } catch (Exception e) {
            log.warn("Error creating configuration file for "+entity, e);
            throw Exceptions.propagate(e);
        }
    }
}
