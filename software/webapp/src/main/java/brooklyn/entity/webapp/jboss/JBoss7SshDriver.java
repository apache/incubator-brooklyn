/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.webapp.jboss;

import static java.lang.String.format;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.webapp.JavaWebAppSshDriver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Strings;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

public class JBoss7SshDriver extends JavaWebAppSshDriver implements JBoss7Driver {

    private static final Logger LOG = LoggerFactory.getLogger(JBoss7SshDriver.class);

    // TODO more configurability of config files, java memory, etc

    public static final String SERVER_TYPE = "standalone";
    public static final String CONFIG_FILE = "standalone-brooklyn.xml";
    public static final String KEYSTORE_FILE = ".keystore";
    public static final String MANAGEMENT_REALM = "ManagementRealm";

    public JBoss7SshDriver(JBoss7ServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public JBoss7ServerImpl getEntity() {
        return (JBoss7ServerImpl) super.getEntity();
    }

    @Override
    public String getSslKeystoreFile() {
        return Os.mergePathsUnix(getRunDir(), SERVER_TYPE, "configuration", KEYSTORE_FILE);
    }
    
    protected String getTemplateConfigurationUrl() {
        return entity.getConfig(JBoss7Server.TEMPLATE_CONFIGURATION_URL);
    }

    @Override
    protected String getLogFileLocation() {
        return Os.mergePathsUnix(getRunDir(), SERVER_TYPE, "log/server.log");
    }

    @Override
    protected String getDeploySubdir() {
        return Os.mergePathsUnix(SERVER_TYPE, "deployments");
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

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        setExpandedInstallDir(getInstallDir()+"/"+resolver.getUnpackedDirectoryName(format("jboss-as-%s", getVersion())));

        List<String> commands = new LinkedList<String>();
        commands.addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs));
        commands.add(BashCommands.INSTALL_TAR);
        commands.add("tar xzfv " + saveAs);

        newScript(INSTALLING)
                // don't set vars yet -- it resolves dependencies (e.g. DB) which we don't want until we start
                .environmentVariablesReset()
                .body.append(commands)
                .execute();
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
        Preconditions.checkState(Strings.isNonBlank(getManagementUsername()), "User for management realm required");
        String managementPassword = getManagementPassword();
        if (Strings.isBlank(managementPassword)) {
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
                // don't set vars yet -- it resolves dependencies (e.g. DB) which we don't want until we start
                .environmentVariablesReset()
                .body.append(
                        format("cp -r %s/%s . || exit $!", getExpandedInstallDir(), SERVER_TYPE),
                        format("echo -e '\n%s=%s' >> %s/%s/configuration/mgmt-users.properties",
                                getManagementUsername(), hashedPassword, getRunDir(), SERVER_TYPE)
                    )
                .execute();

        // Copy the keystore across, if there is one
        if (isProtocolEnabled("HTTPS")) {
            String keystoreUrl = Preconditions.checkNotNull(getSslKeystoreUrl(), "keystore URL must be specified if using HTTPS for "+entity);
            String destinationSslKeystoreFile = getSslKeystoreFile();
            InputStream keystoreStream = resource.getResourceFromUrl(keystoreUrl);
            getMachine().copyTo(keystoreStream, destinationSslKeystoreFile);
        }

        // Copy the configuration file across
        String destinationConfigFile = Os.mergePathsUnix(getRunDir(), SERVER_TYPE, "configuration", CONFIG_FILE);
        copyTemplate(getTemplateConfigurationUrl(), destinationConfigFile);

        // Copy the initial wars to the deploys directory
        getEntity().deployInitialWars();
    }

    @Override
    public void launch() {
        entity.setAttribute(JBoss7Server.PID_FILE, Os.mergePathsUnix(getRunDir(), PID_FILENAME));

        // We wait for evidence of JBoss running because, using
        // brooklyn.ssh.config.tool.class=brooklyn.util.internal.ssh.cli.SshCliTool,
        // we saw the ssh session return before the JBoss process was fully running
        // so the process failed to start.
        newScript(MutableMap.of(USE_PID_FILE, false), LAUNCHING)
                .body.append(
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
                    )
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, true), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, true), STOPPING).environmentVariablesReset().execute();
    }

    @Override
    public void kill() {
        newScript(MutableMap.of(USE_PID_FILE, true), KILLING).execute();
    }

    @Override
    protected List<String> getCustomJavaConfigOptions() {
        return MutableList.<String>builder()
                .addAll(super.getCustomJavaConfigOptions())
                .add("-Xms200m")
                .add("-Xmx800m")
                .add("-XX:MaxPermSize=400m")
                .build();
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
