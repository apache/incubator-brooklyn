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
package org.apache.brooklyn.entity.database.mysql;

import static java.lang.String.format;
import static org.apache.brooklyn.util.JavaGroovyEquivalents.groovyTruth;
import static org.apache.brooklyn.util.ssh.BashCommands.commandsToDownloadUrlsAs;
import static org.apache.brooklyn.util.ssh.BashCommands.installPackage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.location.OsDetails;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.BasicOsDetails.OsVersions;
import org.apache.brooklyn.entity.database.DatastoreMixins;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.io.FileUtil;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.ComparableVersion;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.CountdownTimer;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

/**
 * The SSH implementation of the {@link MySqlDriver}.
 */
public class MySqlSshDriver extends AbstractSoftwareProcessSshDriver implements MySqlDriver {

    public static final Logger log = LoggerFactory.getLogger(MySqlSshDriver.class);

    public MySqlSshDriver(MySqlNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);

        entity.sensors().set(Attributes.LOG_FILE_LOCATION, getLogFile());
    }

    public String getOsTag() {
        // e.g. "osx10.6-x86_64"; see http://www.mysql.com/downloads/mysql/#downloads
        OsDetails os = getLocation().getOsDetails();
        if (os == null) return "linux-glibc2.5-x86_64";
        if (os.isMac()) {
            String osp1 = os.getVersion()==null ? "osx10.8" //lowest common denominator
                : new ComparableVersion(os.getVersion()).isGreaterThanOrEqualTo(OsVersions.MAC_10_9) ? "osx10.9"
                : "osx10.8";  //lowest common denominator
            if (!os.is64bit()) {
                throw new IllegalStateException("Only 64 bit MySQL build is available for OS X");
            }
            return osp1+"-x86_64";
        }
        //assume generic linux
        String osp1 = "linux-glibc2.5";
        String osp2 = os.is64bit() ? "x86_64" : "i686";
        return osp1+"-"+osp2;
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
    public void preInstall() {
        resolver = Entities.newDownloader(this, ImmutableMap.of("filename", getInstallFilename()));
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("mysql-%s-%s", getVersion(), getOsTag()))));
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

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

    @Override
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

        // Wrap in inessential task to allow the stop step to execute even if any of the nested
        // tasks fail - poor man's try-catch for tasks.
        Task<Void> configTask = DynamicTasks.<Void>queue("execute scripts", new Runnable() {
            @Override
            public void run() {
                Tasks.markInessential();
                CountdownTimer timer = Duration.seconds(20).countdownTimer();
                boolean hasCreationScript = copyDatabaseCreationScript();
                timer.waitForExpiryUnchecked();

                changePassword("", getPassword());

                if (hasCreationScript)
                    executeScriptFromInstalledFileAsync("creation-script.sql").asTask().getUnchecked();
            }
        });

        // not sure necessary to stop then subsequently launch, but seems safest
        // (if skipping, use a flag in launch to indicate we've just launched it)
        stop();

        // Fail if any of the tasks above failed, they are marked inessential so the
        // errors don't propagate automatically.
        if (configTask.isError()) {
            configTask.getUnchecked();
        }
    }

    @Override
    public void changePassword(String oldPass, String newPass) {
        DynamicTasks.queue(
            SshEffectorTasks.ssh(
                "cd "+getRunDir(),
                getBaseDir()+"/bin/mysqladmin --defaults-file="+getConfigFile()+" --password=" + oldPass + " password "+newPass)
            .summary("setting password")
            .requiringExitCodeZero());
    }

    protected void copyDatabaseConfigScript() {
        newScript(CUSTOMIZING).execute();  //create the directory

        String configScriptContents = processTemplate(entity.getAttribute(MySqlNode.TEMPLATE_CONFIGURATION_URL));
        Reader configContents = new StringReader(configScriptContents);

        getMachine().copyTo(configContents, Urls.mergePaths(getRunDir(), getConfigFile()));
    }

    protected boolean copyDatabaseCreationScript() {
        String creationScriptContents = DatastoreMixins.getDatabaseCreationScriptAsString(entity);
        if (creationScriptContents==null) return false;

        File templateFile = null;
        BufferedWriter writer = null;
        try {
            templateFile = File.createTempFile("mysql", null);
            FileUtil.setFilePermissionsTo600(templateFile);
            writer = new BufferedWriter(new FileWriter(templateFile));
            writer.write(creationScriptContents);
            writer.flush();
            copyTemplate(templateFile.getAbsoluteFile(), getRunDir() + "/creation-script.sql");
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        } finally {
            if (writer != null) Streams.closeQuietly(writer);
            if (templateFile != null) templateFile.delete();
        }
        return true;
    }

    public String getMySqlServerOptionsString() {
        Map<String, Object> options = entity.getConfig(MySqlNode.MYSQL_SERVER_CONF);
        StringBuilder result = new StringBuilder();
        if (groovyTruth(options)) {
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
        entity.sensors().set(MySqlNode.PID_FILE, getRunDir() + "/" + AbstractSoftwareProcessSshDriver.PID_FILENAME);
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

    @Override
    public ProcessTaskWrapper<Integer> executeScriptAsync(String commands) {
        String filename = "mysql-commands-"+Identifiers.makeRandomId(8);
        DynamicTasks.queue(SshEffectorTasks.put(Urls.mergePaths(getRunDir(), filename)).contents(commands).summary("copying datastore script to execute "+filename));
        return executeScriptFromInstalledFileAsync(filename);
    }

    @Override
    public ProcessTaskWrapper<Integer> executeScriptFromInstalledFileAsync(String filenameAlreadyInstalledAtServer) {
        SshMachineLocation machine = EffectorTasks.getSshMachine(entity);
        return DynamicTasks.queue(
                SshTasks.newSshExecTaskFactory(machine, 
                                "cd "+getRunDir(),
                                getBaseDir()+"/bin/mysql --defaults-file="+getConfigFile()+" < "+filenameAlreadyInstalledAtServer)
                        .requiringExitCodeZero()
                        .summary("executing datastore script "+filenameAlreadyInstalledAtServer));
    }

    @Override
    public ProcessTaskWrapper<Integer> dumpDatabase(String additionalOptions, String dumpDestination) {
        SshMachineLocation machine = EffectorTasks.getSshMachine(entity);
        return DynamicTasks.queue(
                SshTasks.newSshExecTaskFactory(machine, 
                                "cd "+getRunDir(),
                                getBaseDir()+"/bin/mysqldump --defaults-file="+getConfigFile()+" "+additionalOptions+" > "+dumpDestination)
                        .requiringExitCodeZero()
                        .summary("Dumping database to " + dumpDestination));
    }

}
