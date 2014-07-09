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
package brooklyn.entity.database.mysql;

import static brooklyn.util.GroovyJavaMethods.truth;
import static brooklyn.util.ssh.BashCommands.commandsToDownloadUrlsAs;
import static brooklyn.util.ssh.BashCommands.installPackage;
import static brooklyn.util.ssh.BashCommands.ok;
import static java.lang.String.format;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.database.DatastoreMixins;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.BasicOsDetails.OsVersions;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Urls;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.ComparableVersion;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableMap;

/**
 * The SSH implementation of the {@link MySqlDriver}.
 */
public class MySqlSshDriver extends AbstractSoftwareProcessSshDriver implements MySqlDriver {

    public static final Logger log = LoggerFactory.getLogger(MySqlSshDriver.class);

    public MySqlSshDriver(MySqlNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);

        entity.setAttribute(Attributes.LOG_FILE_LOCATION, getLogFile());
    }

    public String getOsTag() {
        // e.g. "osx10.6-x86_64"; see http://www.mysql.com/downloads/mysql/#downloads
        OsDetails os = getLocation().getOsDetails();
        if (os == null) return "linux2.6-i686";
        if (os.isMac()) {
            String osp1 = os.getVersion()==null ? "osx10.5" //lowest common denominator
                : new ComparableVersion(os.getVersion()).isGreaterThanOrEqualTo(OsVersions.MAC_10_6) ? "osx10.6"
                : new ComparableVersion(os.getVersion()).isGreaterThanOrEqualTo(OsVersions.MAC_10_5) ? "osx10.5"
                : "osx10.5";  //lowest common denominator
            String osp2 = os.is64bit() ? "x86_64" : "x86";
            return osp1+"-"+osp2;
        }
        //assume generic linux
        String osp1 = "linux2.6";
        String osp2 = os.is64bit() ? "x86_64" : "i686";
        return osp1+"-"+osp2;
    }

    public String getMirrorUrl() {
        return entity.getConfig(MySqlNode.MIRROR_URL);
    }

    public String getBaseDir() { return getExpandedInstallDir(); }

    public String getDataDir() {
        String result = entity.getConfig(MySqlNode.DATA_DIR);
        return (result == null) ? "." : result;
    }

    public String getLogFile() {
        return Urls.mergePaths(getRunDir(), "console.log");
    }

    public String getConfigFile() {
        return "mymysql.cnf";
    }

    public String getInstallFilename() {
        return String.format("mysql-%s-%s.tar.gz", getVersion(), getOsTag());
    }

    @Override
    public void install() {
        DownloadResolver resolver = Entities.newDownloader(this, ImmutableMap.of("filename", getInstallFilename()));
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        setExpandedInstallDir(getInstallDir() + "/" + resolver.getUnpackedDirectoryName(format("mysql-%s-%s", getVersion(), getOsTag())));

        List<String> commands = new LinkedList<String>();
        commands.add(BashCommands.INSTALL_TAR);
        commands.add(BashCommands.INSTALL_CURL);

        commands.add("echo installing extra packages");
        commands.add(installPackage(ImmutableMap.of("yum", "libgcc_s.so.1"), null));
        commands.add(installPackage(ImmutableMap.of("yum", "libaio.so.1 libncurses.so.5", "apt", "libaio1 libaio-dev"), null));

        // these deps are only needed on some OS versions but others don't need them
        commands.add(installPackage(ImmutableMap.of("yum", "libaio", "apt", "ia32-libs"), null));
        commands.add("echo finished installing extra packages");
        commands.addAll(commandsToDownloadUrlsAs(urls, saveAs));
        commands.add(format("tar xfvz %s", saveAs));

        newScript(INSTALLING).body.append(commands).execute();
    }

    public MySqlNodeImpl getEntity() { return (MySqlNodeImpl) super.getEntity(); }
    public int getPort() { return getEntity().getPort(); }
    public String getSocketUid() { return getEntity().getSocketUid(); }
    public String getPassword() { return getEntity().getPassword(); }

    @Override
    public void customize() {
        copyDatabaseConfigScript();

        newScript(CUSTOMIZING)
            .updateTaskAndFailOnNonZeroResultCode()
            .body.append(
                "chmod 600 "+getConfigFile(),
                getBaseDir()+"/scripts/mysql_install_db "+
                    "--basedir="+getBaseDir()+" --datadir="+getDataDir()+" "+
                    "--defaults-file="+getConfigFile())
            .execute();

        // launch, then we will configure it
        launch();

        CountdownTimer timer = Duration.seconds(20).countdownTimer();
        boolean hasCreationScript = copyDatabaseCreationScript();
        timer.waitForExpiryUnchecked();

        DynamicTasks.queue(
            SshEffectorTasks.ssh(
                "cd "+getRunDir(),
                getBaseDir()+"/bin/mysqladmin --defaults-file="+getConfigFile()+" --password= password "+getPassword()
            ).summary("setting password"));

        if (hasCreationScript)
            executeScriptFromInstalledFileAsync("creation-script.sql");

        // not sure necessary to stop then subsequently launch, but seems safest
        // (if skipping, use a flag in launch to indicate we've just launched it)
        stop();
    }

    private void copyDatabaseConfigScript() {
        newScript(CUSTOMIZING).execute();  //create the directory

        String configScriptContents = processTemplate(entity.getAttribute(MySqlNode.TEMPLATE_CONFIGURATION_URL));
        Reader configContents = new StringReader(configScriptContents);

        getMachine().copyTo(configContents, Urls.mergePaths(getRunDir(), getConfigFile()));
    }

    private boolean copyDatabaseCreationScript() {
        InputStream creationScript = DatastoreMixins.getDatabaseCreationScript(entity);
        if (creationScript==null) return false;
        getMachine().copyTo(creationScript, getRunDir() + "/creation-script.sql");
        return true;
    }

    public String getMySqlServerOptionsString() {
        Map<String, Object> options = entity.getConfig(MySqlNode.MYSQL_SERVER_CONF);
        StringBuilder result = new StringBuilder();
        if (truth(options)) {
            for (Map.Entry<String, Object> entry : options.entrySet()) {
                result.append(entry.getKey());
                String value = entry.getValue().toString();
                if (!Strings.isEmpty(value)) {
                    result.append(" = ").append(value);
                }
                result.append('\n');
            }
        }
        return result.toString();
    }

    @Override
    public void launch() {
        entity.setAttribute(MySqlNode.PID_FILE, getRunDir() + "/" + AbstractSoftwareProcessSshDriver.PID_FILENAME);
        newScript(MutableMap.of("usePidFile", true), LAUNCHING)
            .updateTaskAndFailOnNonZeroResultCode()
            .body.append(format("nohup %s/bin/mysqld --defaults-file=%s --user=`whoami` > %s 2>&1 < /dev/null &", getBaseDir(), getConfigFile(), getLogFile()))
            .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", false), CHECK_RUNNING)
            .body.append(getStatusCmd())
            .execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of("usePidFile", true), STOPPING).execute();
    }

    @Override
    public void kill() {
        newScript(MutableMap.of("usePidFile", true), KILLING).execute();
    }

    @Override
    public String getStatusCmd() {
        return format("%s/bin/mysqladmin --defaults-file=%s status", getBaseDir(), Urls.mergePaths(getRunDir(), getConfigFile()));
    }

    public ProcessTaskWrapper<Integer> executeScriptAsync(String commands) {
        String filename = "mysql-commands-"+Identifiers.makeRandomId(8);
        DynamicTasks.queue(SshEffectorTasks.put(Urls.mergePaths(getRunDir(), filename)).contents(commands).summary("copying datastore script to execute "+filename));
        return executeScriptFromInstalledFileAsync(filename);
    }

    public ProcessTaskWrapper<Integer> executeScriptFromInstalledFileAsync(String filenameAlreadyInstalledAtServer) {
        return DynamicTasks.queue(
                SshEffectorTasks.ssh(
                                "cd "+getRunDir(),
                                getBaseDir()+"/bin/mysql --defaults-file="+getConfigFile()+" < "+filenameAlreadyInstalledAtServer)
                        .summary("executing datastore script "+filenameAlreadyInstalledAtServer));
    }

}
