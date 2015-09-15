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
package org.apache.brooklyn.entity.nosql.mongodb;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.location.OsDetails;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
        List<String> commands = new LinkedList<>();
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
        List<String> commands = new LinkedList<>();
        commands.add(String.format("mkdir -p %s", getDataDirectory()));

        if (MongoDBAuthenticationUtils.usesAuthentication(entity)) {
            String destinationLocation = Os.mergePaths(getRunDir(), "mongodb-keyfile");
            entity.sensors().set(AbstractMongoDBServer.MONGODB_KEYFILE_DESTINATION, destinationLocation);
            String keyfileContents = entity.config().get(AbstractMongoDBServer.MONGODB_KEYFILE_CONTENTS);
            if (Strings.isNullOrEmpty(keyfileContents)) {
                String keyfileUrl = entity.config().get(AbstractMongoDBServer.MONGODB_KEYFILE_URL);
                if (Strings.isNullOrEmpty(keyfileUrl)) {
                    throw new IllegalStateException("MongoDBAuthenticationUtils.usesAuthentication returned true, but neither keyfileContents nor keyfileUrl are set");
                }
                copyResource(keyfileUrl, destinationLocation);
            } else {
                commands.add(BashCommands.pipeTextToFile(keyfileContents, destinationLocation));
            }
            commands.add("chmod 600 " + destinationLocation);
        }

        newScript(CUSTOMIZING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(commands).execute();
        String templateUrl = entity.getConfig(MongoDBServer.MONGODB_CONF_TEMPLATE_URL);
        if (!Strings.isNullOrEmpty(templateUrl)) copyTemplate(templateUrl, getConfFile());
        if (MongoDBAuthenticationUtils.usesAuthentication(entity)) {
            launch(getArgsBuilderWithNoAuthentication((AbstractMongoDBServer) getEntity())
                    .add("--dbpath", getDataDirectory()));
            newScript("create-user")
                    .body.append(String.format("%s --port %s" +
                            " --host localhost admin --eval \"db.createUser({user: '%s',pwd: '%s',roles: [ 'root' ]})\"",
                    Os.mergePaths(getExpandedInstallDir(), "bin/mongo"), getServerPort(), getRootUsername(), MongoDBAuthenticationUtils.getRootPassword(entity)))
                    .updateTaskAndFailOnNonZeroResultCode()
                    .execute();
            stop();
        }
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
                .body.append("MONGO_PID=$(cat " + getPidFile() + ")\n")
                .body.append("kill -2 $MONGO_PID\n")
                .body.append("for i in {1..10}\n" +
                    "do\n" +
                    "    kill -0 $MONGO_PID || exit \n" +
                    "    sleep 1\n" +
                    "done\n" +
                    "echo \"mongoDB process still running after 10 seconds; continuing but may subsequently fail\"")
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

    protected String getRootUsername() {
        return entity.config().get(AbstractMongoDBServer.ROOT_USERNAME);
    }

    protected ImmutableList.Builder<String> getArgsBuilderWithDefaults(AbstractMongoDBServer server) {
        ImmutableList.Builder<String> builder = getArgsBuilderWithNoAuthentication(server);
        if (MongoDBAuthenticationUtils.usesAuthentication(entity)) {
            builder.add("--keyFile", entity.getAttribute(AbstractMongoDBServer.MONGODB_KEYFILE_DESTINATION));
        }
        return builder;
    }

    protected ImmutableList.Builder<String> getArgsBuilderWithNoAuthentication(AbstractMongoDBServer server) {
        Integer port = server.getAttribute(MongoDBServer.PORT);
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add("--config", getConfFile());
        builder.add("--pidfilepath", getPidFile());
        builder.add("--logpath", getLogFile());
        builder.add("--port", port.toString());
        builder.add("--fork");
        return builder;
    }
    
    protected void launch(ImmutableList.Builder<String> argsBuilder) {
        String args = Joiner.on(" ").join(argsBuilder.build());
        String command = String.format("%s/bin/mongod %s >> out.log 2>> err.log < /dev/null", getExpandedInstallDir(), args);

        newScript(LAUNCHING)
                .setFlag(SshTool.PROP_CONNECT_TIMEOUT, Duration.TEN_SECONDS.toMilliseconds())
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(command).execute();
    }

}
