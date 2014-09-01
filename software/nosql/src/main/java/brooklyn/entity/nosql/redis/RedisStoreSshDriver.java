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
package brooklyn.entity.nosql.redis;

import static java.lang.String.format;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;

import com.google.common.collect.ImmutableList;

/**
 * Start a {@link RedisStore} in a {@link Location} accessible over ssh.
 */
public class RedisStoreSshDriver extends AbstractSoftwareProcessSshDriver implements RedisStoreDriver {

    private static final Logger LOG = LoggerFactory.getLogger(RedisStoreSshDriver.class);

    public RedisStoreSshDriver(RedisStoreImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(format("redis-%s", getVersion()))));
    }

    @Override
    public void install() {
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();

        MutableMap<String, String> installGccPackageFlags = MutableMap.of(
                "onlyifmissing", "gcc",
                "yum", "gcc",
                "apt", "gcc",
                "port", null);
        MutableMap<String, String> installMakePackageFlags = MutableMap.of(
                "onlyifmissing", "make",
                "yum", "make",
                "apt", "make",
                "port", null);

        List<String> commands = ImmutableList.<String>builder()
                .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
                .add(BashCommands.INSTALL_TAR)
                .add(BashCommands.INSTALL_CURL)
                .add(BashCommands.installPackage(installGccPackageFlags, "redis-prerequisites-gcc"))
                .add(BashCommands.installPackage(installMakePackageFlags, "redis-prerequisites-make"))
                .add("tar xzfv " + saveAs)
                .add(format("cd redis-%s", getVersion()))
                .add("make clean && make")
                .build();

        newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .body.append(commands).execute();
    }

    @Override
    public void customize() {
        newScript(MutableMap.of("usePidFile", false), CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(
                        format("cd %s", getExpandedInstallDir()),
                        "make install PREFIX="+getRunDir())
                .execute();

        copyTemplate(getEntity().getConfig(RedisStore.REDIS_CONFIG_TEMPLATE_URL), "redis.conf");
    }

    @Override
    public void launch() {
        // TODO Should we redirect stdout/stderr: format(" >> %s/console 2>&1 </dev/null &", getRunDir())
        newScript(MutableMap.of("usePidFile", false), LAUNCHING)
                .failOnNonZeroResultCode()
                .body.append("./bin/redis-server redis.conf")
                .execute();
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", false), CHECK_RUNNING)
                .body.append("./bin/redis-cli -p " + getEntity().getAttribute(RedisStore.REDIS_PORT) + " ping > /dev/null")
                .execute() == 0;
    }

    /**
     * Restarts redis with the current configuration.
     */
    @Override
    public void stop() {
        int exitCode = newScript(MutableMap.of("usePidFile", false), STOPPING)
                .body.append("./bin/redis-cli -p " + getEntity().getAttribute(RedisStore.REDIS_PORT) + " shutdown")
                .execute();
        // TODO: Good enough? Will cause warnings when trying to stop a server that is already not running.
        if (exitCode != 0) {
            LOG.warn("Unexpected exit code when stopping {}: {}", entity, exitCode);
        }
    }
}
