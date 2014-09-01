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
package brooklyn.entity.nosql.mongodb;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.lifecycle.ScriptHelper;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public abstract class AbstractMongoDBSshDriver extends AbstractSoftwareProcessSshDriver {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMongoDBSshDriver.class);
    
    public AbstractMongoDBSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(getBaseName())));
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
    
        List<String> commands = new LinkedList<String>();
        commands.addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs));
        commands.add(BashCommands.INSTALL_TAR);
        commands.add("tar xzfv " + saveAs);
    
        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands).execute();
    }
    
    @Override
    public void customize() {
        Map<?,?> ports = ImmutableMap.of("port", getServerPort());
        Networking.checkPortsValid(ports);
        String command = String.format("mkdir -p %s", getDataDirectory());
        newScript(CUSTOMIZING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(command).execute();
        String templateUrl = entity.getConfig(MongoDBServer.MONGODB_CONF_TEMPLATE_URL);
        if (!Strings.isNullOrEmpty(templateUrl)) copyTemplate(templateUrl, getConfFile());
    }
    
    @Override
    public boolean isRunning() {
        try {
            return MongoDBClientSupport.forServer((AbstractMongoDBServer) entity).ping();
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            return false;
        }
    }
    
    /**
     * Kills the server with SIGINT. Sending SIGKILL is likely to result in data corruption.
     * @see <a href="http://docs.mongodb.org/manual/tutorial/manage-mongodb-processes/#sending-a-unix-int-or-term-signal">http://docs.mongodb.org/manual/tutorial/manage-mongodb-processes/#sending-a-unix-int-or-term-signal</a>
     */
    @Override
    public void stop() {
        // TODO: Wait for process to terminate. Currently, this will send the signal and then immediately continue with next steps, 
        // which could involve stopping VM etc.
        
        // We could also use SIGTERM (15)
        new ScriptHelper(this, "Send SIGINT to MongoDB server")
        .body.append("kill -2 $(cat " + getPidFile() + ")")
        .execute();
    }

    protected String getBaseName() {
        return getOsTag() + "-" + entity.getConfig(AbstractMongoDBServer.SUGGESTED_VERSION);
    }

    // IDE note: This is used by MongoDBServer.DOWNLOAD_URL
    public String getOsDir() {
        return (getLocation().getOsDetails().isMac()) ? "osx" : "linux";
    }

    public String getOsTag() {
        OsDetails os = getLocation().getOsDetails();
        if (os == null) {
            // Default to generic linux
            return "mongodb-linux-x86_64";
        } else if (os.isMac()) {
            // Mac is 64bit only
            return "mongodb-osx-x86_64";
        } else {
            String arch = os.is64bit() ? "x86_64" : "i686";
            return "mongodb-linux-" + arch;
        }
    }

    public String getDataDirectory() {
        String result = entity.getConfig(MongoDBServer.DATA_DIRECTORY);
        if (result!=null) return result;
        return getRunDir() + "/data";
    }

    protected String getLogFile() {
        return getRunDir() + "/log.txt";
    }

    protected String getPidFile() {
        return getRunDir() + "/pid";
    }

    protected Integer getServerPort() {
        return entity.getAttribute(MongoDBServer.PORT);
    }

    protected String getConfFile() {
        return getRunDir() + "/mongo.conf";
    }

    protected ImmutableList.Builder<String> getArgsBuilderWithDefaults(AbstractMongoDBServer server) {
        Integer port = server.getAttribute(MongoDBServer.PORT);

        return ImmutableList.<String>builder()
                .add("--config", getConfFile())
                .add("--pidfilepath", getPidFile())
                .add("--logpath", getLogFile())
                .add("--port", port.toString())
                .add("--fork");
    }
    
    protected void launch(ImmutableList.Builder<String> argsBuilder) {
        String args = Joiner.on(" ").join(argsBuilder.build());
        String command = String.format("%s/bin/mongod %s > out.log 2> err.log < /dev/null", getExpandedInstallDir(), args);
        LOG.info(command);
        newScript(LAUNCHING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(command).execute();
    }
 
}