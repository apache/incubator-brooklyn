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
package brooklyn.entity.software.mysql;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicStartable;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.software.MachineLifecycleEffectorTasks;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.MachineLocation;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.BasicOsDetails.OsVersions;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation.LocalhostMachine;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.task.ssh.SshTasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.ComparableVersion;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterables;

public class DynamicToyMySqlEntityBuilder {

    private static final Logger log = LoggerFactory.getLogger(DynamicToyMySqlEntityBuilder.class);

    public static EntitySpec<? extends Entity> spec() {
        return EntitySpec.create(BasicStartable.class).addInitializer(MySqlEntityInitializer.class);
    }

    public static final String downloadUrl(Entity e, boolean isLocalhost) {
        if (isLocalhost) {
            for (int i=50; i>20; i--) {
                String f = System.getProperty("user.home")+"/.brooklyn/repository/MySqlNode/5.5."+i+"/mysql-5.5."+i+"-osx10.6-x86_64.tar.gz";
                if (new File(f).exists())
                    return "file://"+f;
            }
        }
        // download
        String version = "5.5.37";
        String osTag = getOsTag(e);
        String mirrorUrl = "http://www.mirrorservice.org/sites/ftp.mysql.com/";
        return "http://dev.mysql.com/get/Downloads/MySQL-5.5/mysql-"+version+"-"+osTag+".tar.gz/from/"+mirrorUrl;
    }

    public static final String installDir(Entity e, boolean isLocalhost) {
        String url = downloadUrl(e, isLocalhost);
        String archive = Iterables.find(Splitter.on('/').omitEmptyStrings().split(url), Predicates.containsPattern(".tar.gz"));
        return archive.replace(".tar.gz", "");
    }

    public static final String dir(Entity e) {
        return "/tmp/brooklyn-mysql-"+e.getId();
    }

    // copied from MySqlSshDriver
    public static String getOsTag(Entity e) {
        // e.g. "osx10.6-x86_64"; see http://www.mysql.com/downloads/mysql/#downloads
        OsDetails os = ((SshMachineLocation)Iterables.getOnlyElement(e.getLocations())).getOsDetails();
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

    public static class MySqlEntityInitializer implements EntityInitializer {
        public void apply(final EntityLocal entity) {
          new MachineLifecycleEffectorTasks() {
            @Override
            protected String startProcessesAtMachine(Supplier<MachineLocation> machineS) {
                DynamicTasks.queue(
                        SshEffectorTasks.ssh(
                            "mkdir "+dir(entity),
                            "cd "+dir(entity),
                            BashCommands.downloadToStdout(downloadUrl(entity, isLocalhost(machineS)))+" | tar xvz"
                        ).summary("download mysql").returning(SshTasks.returningStdoutLoggingInfo(log, true)));
                if (isLinux(machineS)) {
                    DynamicTasks.queue(SshEffectorTasks.ssh(BashCommands.installPackage("libaio1")));
                }
                DynamicTasks.queue(
                        SshEffectorTasks.put(".my.cnf")
                            .contents(String.format("[mysqld]\nbasedir=%s/%s\n", dir(entity), installDir(entity, isLocalhost(machineS)))),
                        SshEffectorTasks.ssh(
                            "cd "+dir(entity)+"/*",
                            "./scripts/mysql_install_db",
                            "./support-files/mysql.server start > out.log 2> err.log < /dev/null"
                        ).summary("setup and run mysql").returning(SshTasks.returningStdoutLoggingInfo(log, true)));
                return "submitted start";
            }
            protected void postStartCustom() {
                // if it's still up after 5s assume we are good
                Time.sleep(Duration.FIVE_SECONDS);
                if (!DynamicTasks.queue(SshEffectorTasks.isPidFromFileRunning(dir(entity)+"/*/data/*.pid")).get()) {
                    // but if it's not up add a bunch of other info
                    log.warn("MySQL did not start: "+dir(entity));
                    ProcessTaskWrapper<Integer> info = DynamicTasks.queue(SshEffectorTasks.ssh(
                            "cd "+dir(entity)+"/*",
                            "cat out.log",
                            "cat err.log > /dev/stderr")).block();
                    log.info("STDOUT:\n"+info.getStdout());
                    log.info("STDERR:\n"+info.getStderr());
                    BrooklynTaskTags.addTagsDynamically(Tasks.current(), 
                        BrooklynTaskTags.tagForStream("console (nohup stdout)", Suppliers.ofInstance(info.getStdout()), null),
                        BrooklynTaskTags.tagForStream("console (nohup stderr)", Suppliers.ofInstance(info.getStderr()), null));
                    throw new IllegalStateException("MySQL appears not to be running");
                }

                // and set the PID
                entity().setAttribute(Attributes.PID, 
                        Integer.parseInt(DynamicTasks.queue(SshEffectorTasks.ssh("cat "+dir(entity)+"/*/data/*.pid")).block().getStdout().trim()));
            }

            @Override
            protected String stopProcessesAtMachine() {
                Integer pid = entity().getAttribute(Attributes.PID);
                if (pid==null) {
                    log.info("mysql not running");
                    return "No pid -- is it running?";
                }

                DynamicTasks.queue(SshEffectorTasks.ssh(
                        "cd "+dir(entity)+"/*",
                        "./support-files/mysql.server stop"
                    ).summary("stop mysql"));
                return "submitted stop";
            }
          }.attachLifecycleEffectors(entity);
      }
    }

    private static boolean isLocalhost(Supplier<MachineLocation> machineS) {
        return machineS.get() instanceof LocalhostMachine;
    }

    private static boolean isLinux(Supplier<MachineLocation> machineS) {
        return machineS.get().getMachineDetails().getOsDetails().isLinux();
    }

}
