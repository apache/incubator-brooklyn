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
package brooklyn.entity.messaging.storm;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import brooklyn.entity.zookeeper.ZooKeeperEnsemble;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class StormSshDriver extends JavaSoftwareProcessSshDriver implements StormDriver {

    private static final Logger log = LoggerFactory.getLogger(StormSshDriver.class);

    public StormSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    public String getRoleName() {
        return entity.getConfig(Storm.ROLE).name().toLowerCase();
    }

    public String getZeromqVersion() {
        return entity.getConfig(Storm.ZEROMQ_VERSION);
    }

    public String getLocalDir() {
        return Optional.fromNullable(entity.getConfig(Storm.LOCAL_DIR)).or(Os.mergePathsUnix(getRunDir(), "storm"));
    }

    public String getNimbusHostname() {
        String result = entity.getConfig(Storm.NIMBUS_HOSTNAME);
        if (result != null) return result;

        Entity nimbus = entity.getConfig(Storm.NIMBUS_ENTITY);
        if (nimbus == null) {
            log.warn("No nimbus hostname available; using 'localhost'");
            return "localhost";
        }
        return Entities.submit(entity, DependentConfiguration.attributeWhenReady(nimbus, Attributes.HOSTNAME)).getUnchecked();
    }

    public Integer getUiPort() {
        return entity.getAttribute(Storm.UI_PORT);
    }

    public Map<String, Integer> getPortMap() {
        return MutableMap.of("uiPort", getUiPort());
    }

    @Override
    protected List<String> getCustomJavaConfigOptions() {
        List<String> result = super.getCustomJavaConfigOptions();
        if ("nimbus".equals(getRoleName()) || "supervisor".equals(getRoleName())) {
            result.add("-verbose:gc");
            result.add("-XX:+PrintGCTimeStamps");
            result.add("-XX:+PrintGCDetails");
        }

        if ("ui".equals(getRoleName())) {
            result.add("-Xmx768m");
        }

        return result;
    }

    public String getJvmOptsLine() {
        return Optional.fromNullable(getShellEnvironment().get("JAVA_OPTS")).or("");
    }
    
    public List<String> getZookeeperServers() {
        ZooKeeperEnsemble zooKeeperEnsemble = entity.getConfig(Storm.ZOOKEEPER_ENSEMBLE);
        Supplier<List<String>> supplier = Entities.attributeSupplierWhenReady(zooKeeperEnsemble, ZooKeeperEnsemble.ZOOKEEPER_SERVERS);
        return supplier.get();
    }

    public String getStormConfigTemplateUrl() {
        return entity.getConfig(Storm.STORM_CONFIG_TEMPLATE_URL);
    }

    @Override
    public void install() {
        log.debug("Installing {}", entity);
        DownloadResolver resolver = Entities.newDownloader(this);
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        setExpandedInstallDir(getInstallDir() + "/" + resolver.getUnpackedDirectoryName(format("storm-%s", getVersion())));
        
        ImmutableList.Builder<String> commands= ImmutableList.<String> builder();
        if (!getLocation().getOsDetails().isMac()) {
            commands.add(BashCommands.installPackage(ImmutableMap.of(
                        "yum", "libuuid-devel",
                        "apt", "build-essential uuid-dev pkg-config libtool automake"), 
                    "libuuid-devel"));
            commands.add(BashCommands.ifExecutableElse0("yum", BashCommands.sudo("yum -y groupinstall 'Development Tools'")));
        }
        commands.add(BashCommands.installPackage(ImmutableMap.of("yum", "git"), "git"))
                .add(BashCommands.INSTALL_UNZIP)
                .addAll(installNativeDependencies())
                .addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs))
                .add("unzip " + saveAs)
                .add("mkdir -p " + getLocalDir())
                .add("chmod 777 " + getLocalDir()); // FIXME
        newScript(INSTALLING)
                .body.append(commands.build())
                .gatherOutput()
                .execute();
    }

    public String getPidFile() {
        return Os.mergePathsUnix(getRunDir(), format("%s.pid", getRoleName()));
    }

    @Override
    protected String getLogFileLocation() {
        return Os.mergePathsUnix(getRunDir(), "logs", format("%s.log", getRoleName()));
    }

    @Override
    public void launch() {
        boolean needsSleep = false;
        if (getRoleName().equals("supervisor")) {
            Entity nimbus = entity.getConfig(Storm.NIMBUS_ENTITY);
            if (nimbus == null) {
                log.warn("No nimbus entity available; not blocking before starting supervisors");
            } else {
                Entities.waitForServiceUp(nimbus, entity.getConfig(SoftwareProcess.START_TIMEOUT));
                needsSleep = true;
            }
        }

        String subnetHostname = Machines.findSubnetOrPublicHostname(entity).get();
        log.info("Launching " + entity + " with role " + getRoleName() + " and " + "hostname (public) " 
                + getEntity().getAttribute(Attributes.HOSTNAME) + ", " + "hostname (subnet) " + subnetHostname + ")");

        // ensure only one node at a time tries to start
        // attempting to eliminate the causes of:
        // 2013-12-12 09:21:45 supervisor [ERROR] Error on initialization of server mk-supervisor
        // org.apache.zookeeper.KeeperException$NoNodeException: KeeperErrorCode = NoNode for /assignments
        // TODO use SoftwareProcess#START_LATCH instead here?

        Object startMutex = Optional.fromNullable(entity.getConfig(Storm.START_MUTEX)).or(new Object());
        synchronized (startMutex) {
            if (needsSleep) {
                // give 10s extra to make sure nimbus is ready; we see weird zookeeper no /assignments node error otherwise
                // (this could be optimized by recording nimbus service_up time)
                Time.sleep(Duration.TEN_SECONDS);
            }
            newScript(MutableMap.of(USE_PID_FILE, getPidFile()), LAUNCHING)
                    .body.append(format("nohup ./bin/storm %s > %s 2>&1 &", getRoleName(), getLogFileLocation()))
                    .execute();
        }
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of(USE_PID_FILE, getPidFile()), CHECK_RUNNING).execute() == 0;
    }

    @Override
    public void stop() {
        newScript(MutableMap.of(USE_PID_FILE, getPidFile()), STOPPING).execute();
    }

    @Override
    public void customize() {
        log.debug("Customizing {}", entity);
        Networking.checkPortsValid(getPortMap());

        newScript(CUSTOMIZING)
                .body.append(format("cp -R %s/* .", getExpandedInstallDir()))
                .execute();

        String destinationConfigFile = Os.mergePathsUnix(getRunDir(), "conf/storm.yaml");
        copyTemplate(getStormConfigTemplateUrl(), destinationConfigFile);
    }

    protected List<String> installNativeDependencies() {
        String zeromqUrl = format("http://download.zeromq.org/zeromq-%s.tar.gz", getZeromqVersion());
        String targz = format("zeromq-%s.tar.gz", getZeromqVersion());
        String jzmq = "https://github.com/nathanmarz/jzmq.git";

        ImmutableList.Builder<String> commands = ImmutableList.<String>builder();
        if (getLocation().getOsDetails().isMac()) {
            commands.add("export PATH=$PATH:/usr/local/bin")
                   .add("export JAVA_HOME=$(/usr/libexec/java_home)")
                   .add("cd " + getInstallDir())
                   .add(BashCommands.installPackage(ImmutableMap.of("brew", "automake"), "make"))
                   .add(BashCommands.installPackage(ImmutableMap.of("brew", "libtool"), "libtool"))
                   .add(BashCommands.installPackage(ImmutableMap.of("brew", "pkg-config"), "pkg-config"))
                   .add(BashCommands.installPackage(ImmutableMap.of("brew", "zeromq"), "zeromq"))
                   .add("git clone https://github.com/asmaier/jzmq")
                   .add("cd jzmq")
                   .add("./autogen.sh")
                   .add("./configure")
                   .add("make")
                   .add((BashCommands.sudo("make install")))
                   .add("cd " + getInstallDir());
        } else {
            commands.add("export JAVA_HOME=$(dirname $(readlink -m `which java`))/../../ || export JAVA_HOME=/usr/lib/jvm/java")
                   .add("cd " + getInstallDir())
                   .add(BashCommands.commandToDownloadUrlAs(zeromqUrl, targz))
                   .add("tar xzf " + targz)
                   .add(format("cd zeromq-%s", getZeromqVersion()))
                   .add("./configure")
                   .add("make")
                   .add((BashCommands.sudo("make install")))
                   // install jzmq
                   .add("cd " + getInstallDir())
                   .add("git clone " + jzmq)
                   .add("cd jzmq")
                   .add("./autogen.sh")
                   .add("./configure")
                           
                   // hack needed on ubuntu 12.04; ignore if it fails
                   // see https://github.com/zeromq/jzmq/issues/114
                   .add(BashCommands.ok(
                       "pushd src ; touch classdist_noinst.stamp ; CLASSPATH=.:./.:$CLASSPATH "
                       + "javac -d . org/zeromq/ZMQ.java org/zeromq/App.java org/zeromq/ZMQForwarder.java org/zeromq/EmbeddedLibraryTools.java org/zeromq/ZMQQueue.java org/zeromq/ZMQStreamer.java org/zeromq/ZMQException.java"))
                   .add(BashCommands.ok("popd"))

                   .add("make")
                   .add((BashCommands.sudo("make install")))
                   .add("cd " + getInstallDir());
        }
        return commands.build();
    }

}
