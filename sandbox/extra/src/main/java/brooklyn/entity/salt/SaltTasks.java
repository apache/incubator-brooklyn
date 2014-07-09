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
package brooklyn.entity.salt;

import static brooklyn.util.ssh.BashCommands.INSTALL_CURL;
import static brooklyn.util.ssh.BashCommands.INSTALL_TAR;
import static brooklyn.util.ssh.BashCommands.INSTALL_UNZIP;
import static brooklyn.util.ssh.BashCommands.downloadToStdout;
import static brooklyn.util.ssh.BashCommands.sudo;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.effector.EffectorTasks;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.management.TaskFactory;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Urls;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.annotations.Beta;

@Beta
public class SaltTasks {

    public static TaskFactory<?> installSaltMaster(Entity master, String saltDirectory, boolean force) {
        // TODO check on entity whether it is salt _server_
        String boostrapUrl = master.getConfig(SaltStackMaster.BOOTSTRAP_URL);
        String version = master.getConfig(SaltStackMaster.SUGGESTED_VERSION);
        String installCmd = cdAndRun(saltDirectory,
                BashCommands.chain(
                        INSTALL_CURL,
                        INSTALL_TAR,
                        INSTALL_UNZIP,
                        "( "+downloadToStdout(boostrapUrl) + " | " + sudo("sh -s -- -M -N "+version)+" )"));
        if (!force) installCmd = BashCommands.alternatives("which salt-master", installCmd);
        return SshEffectorTasks.ssh(installCmd).summary("install salt master");
    }

    public static TaskFactory<?> installSaltMinion(final Entity minion, final String runDir, final String installDir, final boolean force) {
        return Tasks.<Void>builder().name("install minion").body(
                new Runnable() {
                    public void run() {
                        // Setup bootstrap installation command for minion
                        String boostrapUrl = minion.getConfig(SaltStackMaster.BOOTSTRAP_URL);
                        String installCmd = cdAndRun(runDir, BashCommands.chain(
                                INSTALL_CURL,
                                INSTALL_TAR,
                                INSTALL_UNZIP,
                                "( "+downloadToStdout(boostrapUrl) + " | " + sudo("sh")+" )"));
                        if (!force) installCmd = BashCommands.alternatives("which salt-minion", installCmd);

                        // Process the minion configuration template
                        Boolean masterless = minion.getConfig(SaltConfig.MASTERLESS_MODE);
                        String url = masterless ? Entities.getRequiredUrlConfig(minion, SaltConfig.MASTERLESS_CONFIGURATION_URL)
                                                : Entities.getRequiredUrlConfig(minion, SaltConfig.MINION_CONFIGURATION_URL);
                        Map<String, Object> config = MutableMap.<String, Object>builder()
                                .put("entity", minion)
                                .put("runDir", runDir)
                                .put("installDir", installDir)
                                .put("formulas", minion.getConfig(SaltConfig.SALT_FORMULAS))
                                .build();
                        String contents = TemplateProcessor.processTemplateContents(new ResourceUtils(minion).getResourceAsString(url), config);

                        // Copy the file contents to the remote machine and install/start salt-minion
                        DynamicTasks.queue(
                                SshEffectorTasks.ssh(installCmd),
                                SshEffectorTasks.put("/tmp/minion")
                                        .contents(contents)
                                        .createDirectory(),
                                SshEffectorTasks.ssh(sudo("mv /tmp/minion /etc/salt/minion")), // TODO clunky
                                SshEffectorTasks.ssh(sudo("restart salt-minion"))
                            );
                    }
                }).buildFactory();
    }

    public static TaskFactory<?> installFormulas(final String installDir, final Map<String,String> formulasAndUrls, final boolean force) {
        return Tasks.<Void>builder().name("install formulas").body(
                new Runnable() {
                    public void run() {
                        Entity e = EffectorTasks.findEntity();
                        if (formulasAndUrls==null)
                            throw new IllegalStateException("No formulas defined to install at "+e);
                        for (String formula: formulasAndUrls.keySet())
                            DynamicTasks.queue(installFormula(installDir, formula, formulasAndUrls.get(formula), force));
                    }
                }).buildFactory();
    }

    public static TaskFactory<?> installFormula(String installDir, String formula, String url, boolean force) {
        return SshEffectorTasks.ssh(cdAndRun(installDir, SaltBashCommands.downloadAndExpandFormula(url, formula, force)))
                .summary("install formula "+formula)
                .requiringExitCodeZero();
    }

    protected static String cdAndRun(String targetDirectory, String command) {
        return BashCommands.chain(
                "mkdir -p "+targetDirectory,
                "cd "+targetDirectory,
                command);
    }

    public static TaskFactory<?> buildSaltFile(String runDir, Iterable<? extends String> runList, Map<String, Object> attributes) {
        StringBuilder top =  new StringBuilder()
                .append("base:\n")
                .append("    '*':\n");
        for (String run : runList) {
            top.append("      - " + run + "\n");
        }

        return SshEffectorTasks.put(Urls.mergePaths(runDir, "base", "top.sls"))
                .contents(top.toString())
                .summary("build salt top file")
                .createDirectory();
    }

    public static TaskFactory<?> runSalt(String runDir) {
        return SshEffectorTasks.ssh(cdAndRun(runDir, BashCommands.sudo("salt-call state.highstate")))
                .summary("run salt install")
                .requiringExitCodeZero();
    }
    
}
