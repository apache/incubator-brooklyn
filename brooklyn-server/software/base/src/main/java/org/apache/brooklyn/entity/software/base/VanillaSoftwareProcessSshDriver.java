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
package org.apache.brooklyn.entity.software.base;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.drivers.downloads.DownloadResolver;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.objs.BrooklynObjectInternal.ConfigurationSupportInternal;
import org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.file.ArchiveUtils;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableMap;

public class VanillaSoftwareProcessSshDriver extends AbstractSoftwareProcessSshDriver implements VanillaSoftwareProcessDriver {

    public VanillaSoftwareProcessSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    String downloadedFilename = null;

    /**
     * Needed because the download url and install commands are likely different for different VanillaSoftwareProcesses!
     * This is particularly true for YAML entities. We take a hash of the download_url, install_command and environment variables.
     * We thus assume any templating of the script has already been done by this point.
     */
    @Override
    protected String getInstallLabelExtraSalt() {
        // run non-blocking in case a value set later is used (e.g. a port)
        Integer hash = hashCodeIfResolved(SoftwareProcess.DOWNLOAD_URL.getConfigKey(), 
            VanillaSoftwareProcess.INSTALL_COMMAND, SoftwareProcess.SHELL_ENVIRONMENT);
        
        // if any of the above blocked then we must make a unique install label,
        // as other yet-unknown config is involved 
        if (hash==null) return Identifiers.makeRandomId(8);
        
        // a user-friendly hash is nice, but tricky since it would have to be short; 
        // go with a random one unless it's totally blank
        if (hash==0) return "default";
        return Identifiers.makeIdFromHash(hash);
    }
    
    private Integer hashCodeIfResolved(ConfigKey<?> ...keys) {
        int hash = 0;
        for (ConfigKey<?> k: keys) {
            Maybe<?> value = ((ConfigurationSupportInternal)getEntity().config()).getNonBlocking(k);
            if (value.isAbsent()) return null;
            hash = hash*31 + (value.get()==null ? 0 : value.get().hashCode());
        }
        return hash;
    }

    @Override
    public void install() {
        Maybe<Object> url = getEntity().getConfigRaw(SoftwareProcess.DOWNLOAD_URL, true);
        if (url.isPresentAndNonNull()) {
            DownloadResolver resolver = Entities.newDownloader(this);
            List<String> urls = resolver.getTargets();
            downloadedFilename = resolver.getFilename();

            List<String> commands = new LinkedList<String>();
            commands.addAll(BashCommands.commandsToDownloadUrlsAs(urls, downloadedFilename));
            commands.addAll(ArchiveUtils.installCommands(downloadedFilename));

            int result = newScript(ImmutableMap.of(INSTALL_INCOMPLETE, true), INSTALLING)
                    .failOnNonZeroResultCode(false)
                    .body.append(commands)
                    .execute();
            
            if (result!=0) {
                // could not install at remote machine; try resolving URL here and copying across
                for (String urlI: urls) {
                    result = ArchiveUtils.install(getMachine(), urlI, Urls.mergePaths(getInstallDir(), downloadedFilename));
                    if (result==0) 
                        break;
                }
                if (result != 0) 
                    throw new IllegalStateException("Error installing archive: " + downloadedFilename);
            }
        }
        
        // If downloadUrl did partial install (see INSTALL_INCOMPLETE above) then always execute install so mark it as completed.
        String installCommand = getEntity().getConfig(VanillaSoftwareProcess.INSTALL_COMMAND);
        if (url.isPresentAndNonNull() && Strings.isBlank(installCommand)) installCommand = "# mark as complete";
        
        if (Strings.isNonBlank(installCommand)) {
            newScript(INSTALLING)
                .failOnNonZeroResultCode()
                .environmentVariablesReset(getShellEnvironment())
                .body.append(installCommand)
                .execute();
        }
    }

    @Override
    public void customize() {
        if (downloadedFilename != null) {
            newScript(CUSTOMIZING)
                    .failOnNonZeroResultCode()
                    // don't set vars yet -- it resolves dependencies (e.g. DB) which we don't want until we start
                    .environmentVariablesReset()
                    .body.append(ArchiveUtils.extractCommands(downloadedFilename, getInstallDir()))
                    .execute();
        }
        
        String customizeCommand = getEntity().getConfig(VanillaSoftwareProcess.CUSTOMIZE_COMMAND);
        
        if (Strings.isNonBlank(customizeCommand)) {
            newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(customizeCommand)
                .execute();
        }
    }

    @Override
    public Map<String, String> getShellEnvironment() {
        return MutableMap.copyOf(super.getShellEnvironment()).add("PID_FILE", getPidFile());
    }

    public String getPidFile() {
        // TODO see note in VanillaSoftwareProcess about PID_FILE as a config key
        // if (getEntity().getConfigRaw(PID_FILE, includeInherited)) ...
        return Os.mergePathsUnix(getRunDir(), PID_FILENAME);
    }

    @Override
    public void launch() {
        newScript(LAUNCHING)
            .failOnNonZeroResultCode()
            .body.append(getEntity().getConfig(VanillaSoftwareProcess.LAUNCH_COMMAND))
            .execute();
    }

    @Override
    public boolean isRunning() {
        String customCommand = getEntity().getConfig(VanillaSoftwareProcess.CHECK_RUNNING_COMMAND);
        ScriptHelper script = null;
        if (customCommand == null) {
            script = newScript(MutableMap.of(USE_PID_FILE, getPidFile()), CHECK_RUNNING);
        } else {
            // TODO: template substitutions?
            script = newScript(CHECK_RUNNING).body.append(customCommand);
        }
        return script.execute() == 0;
    }

    @Override
    public void stop() {
        String customCommand = getEntity().getConfig(VanillaSoftwareProcess.STOP_COMMAND);
        ScriptHelper script = null;
        if (customCommand == null) {
            script = newScript(MutableMap.of(USE_PID_FILE, getPidFile()), STOPPING);
        } else {
            // TODO: template substitutions?
            script = newScript(STOPPING).body.append(customCommand);
        }
        script.execute();
    }

}
