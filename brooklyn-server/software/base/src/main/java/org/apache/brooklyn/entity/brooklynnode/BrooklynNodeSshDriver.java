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
package org.apache.brooklyn.entity.brooklynnode;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.drivers.downloads.DownloadSubstituters;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNode.ExistingFileBehaviour;
import org.apache.brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.file.ArchiveBuilder;
import org.apache.brooklyn.util.core.file.ArchiveUtils;
import org.apache.brooklyn.util.core.internal.ssh.SshTool;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class BrooklynNodeSshDriver extends JavaSoftwareProcessSshDriver implements BrooklynNodeDriver {
    
    public BrooklynNodeSshDriver(BrooklynNodeImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public BrooklynNodeImpl getEntity() {
        return (BrooklynNodeImpl) super.getEntity();
    }

    public String getBrooklynHome() {
        return getRunDir();
    }

    @Override
    protected String getLogFileLocation() {
        return Os.mergePathsUnix(getRunDir(), "console");
    }
    
    protected String getPidFile() {
        return Os.mergePathsUnix(getRunDir(), "pid_java");
    }
    
    @Override
    protected String getInstallLabelExtraSalt() {
        String downloadUrl = entity.getConfig(BrooklynNode.DOWNLOAD_URL);
        String uploadUrl = entity.getConfig(BrooklynNode.DISTRO_UPLOAD_URL);
        if (Objects.equal(downloadUrl, BrooklynNode.DOWNLOAD_URL.getConfigKey().getDefaultValue()) &&
                Objects.equal(uploadUrl, BrooklynNode.DISTRO_UPLOAD_URL.getDefaultValue())) {
            // if both are at the default value, then no salt
            return null;
        }
        return Identifiers.makeIdFromHash(Objects.hashCode(downloadUrl, uploadUrl));
    }

    @Override
    public void preInstall() {
        resolver = Entities.newDownloader(this);
        String subpath = entity.getConfig(BrooklynNode.SUBPATH_IN_ARCHIVE);
        if (subpath==null) {
            // assume the dir name is `basename-VERSION` where download link is `basename-VERSION-dist.tar.gz`
            String uploadUrl = entity.getConfig(BrooklynNode.DISTRO_UPLOAD_URL);
            String origDownloadName = uploadUrl;
            if (origDownloadName==null) 
                origDownloadName = entity.getAttribute(BrooklynNode.DOWNLOAD_URL);
            if (origDownloadName!=null) {
                // BasicDownloadResolver makes it crazy hard to get the template-evaluated value of DOWNLOAD_URL
                origDownloadName = DownloadSubstituters.substitute(origDownloadName, DownloadSubstituters.getBasicEntitySubstitutions(this));
                origDownloadName = Urls.decode(origDownloadName);
                origDownloadName = Urls.getBasename(origDownloadName);
                String downloadName = origDownloadName;
                downloadName = Strings.removeFromEnd(downloadName, ".tar.gz");
                downloadName = Strings.removeFromEnd(downloadName, ".tgz");
                downloadName = Strings.removeFromEnd(downloadName, ".zip");
                if (!downloadName.equals(origDownloadName)) {
                    downloadName = Strings.removeFromEnd(downloadName, "-dist");
                    subpath = downloadName;
                }
            }
        }
        if (subpath==null) subpath = format("brooklyn-dist-%s", getVersion());
        setExpandedInstallDir(Os.mergePaths(getInstallDir(), resolver.getUnpackedDirectoryName(subpath)));
    }

    @Override
    public void clearInstallDir() {
        super.setInstallDir(null);
        super.setExpandedInstallDir(null);
    }
    
    @Override
    public void install() {
        String uploadUrl = entity.getConfig(BrooklynNode.DISTRO_UPLOAD_URL);
        
        // Need to explicitly give file, because for snapshot URLs you don't get a clean filename from the URL.
        // This filename is used to generate the first URL to try: [BROOKLYN_VERSION_BELOW]
        // file://$HOME/.brooklyn/repository/BrooklynNode/0.9.0-SNAPSHOT/brooklynnode-0.8.0-snapshot.tar.gz
        // (DOWNLOAD_URL overrides this and has a default which comes from maven)
        List<String> urls = resolver.getTargets();
        String saveAs = resolver.getFilename();
        
        newScript("createInstallDir")
                .body.append("mkdir -p "+getInstallDir())
                .failOnNonZeroResultCode()
                .execute();

        List<String> commands = Lists.newArrayList();
        // TODO use machine.installTo ... but that only works w a single location currently
        if (uploadUrl != null) {
            // Only upload if not already installed
            boolean exists = newScript("checkIfInstalled")
                    .body.append("cd "+getInstallDir(), "test -f BROOKLYN")
                    .execute() == 0;
            if (!exists) {
                InputStream distroStream = resource.getResourceFromUrl(uploadUrl);
                getMachine().copyTo(distroStream, getInstallDir()+"/"+saveAs);
            }
        } else {
            commands.addAll(BashCommands.commandsToDownloadUrlsAs(urls, saveAs));
        }
        commands.add(BashCommands.INSTALL_TAR);
        commands.add("tar xzfv " + saveAs);
        
        newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(commands).execute();
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(
                        // workaround for AMP distribution placing everything in the root of this archive, but
                        // brooklyn distribution placing everything in a subdirectory: check to see if subdirectory
                        // with expected name exists; symlink to same directory if it doesn't
                        // FIXME remove when all downstream usages don't use this
                        format("[ -d %1$s ] || ln -s . %1$s", getExpandedInstallDir(), getExpandedInstallDir()),
                        
                        // previously we only copied bin,conf and set BROOKLYN_HOME to the install dir;
                        // but that does not play nicely if installing dists other than brooklyn
                        // (such as what is built by our artifact)  
                        format("cp -R %s/* .", getExpandedInstallDir()),
                        "mkdir -p ./lib/dropins/")
                .execute();
        
        SshMachineLocation machine = getMachine();
        BrooklynNode entity = getEntity();
        
        String brooklynGlobalPropertiesRemotePath = entity.getConfig(BrooklynNode.BROOKLYN_GLOBAL_PROPERTIES_REMOTE_PATH);
        String brooklynGlobalPropertiesContents = entity.getConfig(BrooklynNode.BROOKLYN_GLOBAL_PROPERTIES_CONTENTS);
        String brooklynGlobalPropertiesUri = entity.getConfig(BrooklynNode.BROOKLYN_GLOBAL_PROPERTIES_URI);

        String brooklynLocalPropertiesRemotePath = processTemplateContents(entity.getConfig(BrooklynNode.BROOKLYN_LOCAL_PROPERTIES_REMOTE_PATH));
        String brooklynLocalPropertiesContents = entity.getConfig(BrooklynNode.BROOKLYN_LOCAL_PROPERTIES_CONTENTS);
        String brooklynLocalPropertiesUri = entity.getConfig(BrooklynNode.BROOKLYN_LOCAL_PROPERTIES_URI);

        String brooklynCatalogRemotePath = entity.getConfig(BrooklynNode.BROOKLYN_CATALOG_REMOTE_PATH);
        String brooklynCatalogContents = entity.getConfig(BrooklynNode.BROOKLYN_CATALOG_CONTENTS);
        String brooklynCatalogUri = entity.getConfig(BrooklynNode.BROOKLYN_CATALOG_URI);

        // Override the ~/.brooklyn/brooklyn.properties if required
        if (brooklynGlobalPropertiesContents != null || brooklynGlobalPropertiesUri != null) {
            ExistingFileBehaviour onExisting = entity.getConfig(BrooklynNode.ON_EXISTING_PROPERTIES_FILE);
            Integer checkExists = DynamicTasks.queue(SshEffectorTasks.ssh("ls \""+brooklynGlobalPropertiesRemotePath+"\"").allowingNonZeroExitCode()).get();
            boolean doUpload = true;
            if (checkExists==0) {
                switch (onExisting) {
                case USE_EXISTING: doUpload = false; break;
                case OVERWRITE: break;
                case DO_NOT_USE: 
                    throw new IllegalStateException("Properties file "+brooklynGlobalPropertiesContents+" already exists and "+
                        "even though it is not being used, content for it was supplied");
                case FAIL: 
                    throw new IllegalStateException("Properties file "+brooklynGlobalPropertiesContents+" already exists and "+
                        BrooklynNode.ON_EXISTING_PROPERTIES_FILE+" response is to fail");
                default:
                    throw new IllegalStateException("Properties file "+brooklynGlobalPropertiesContents+" already exists and "+
                        BrooklynNode.ON_EXISTING_PROPERTIES_FILE+" response "+onExisting+" is unknown");
                }
            }
            if (onExisting==ExistingFileBehaviour.DO_NOT_USE) {
                log.warn("Global properties supplied when told not to use them; no global properties exists, so it will be installed, but it will not be used.");
            }
            if (doUpload)
                uploadFileContents(brooklynGlobalPropertiesContents, brooklynGlobalPropertiesUri, brooklynGlobalPropertiesRemotePath);
        }
        
        // Upload a local-brooklyn.properties if required
        if (brooklynLocalPropertiesContents != null || brooklynLocalPropertiesUri != null) {
            uploadFileContents(brooklynLocalPropertiesContents, brooklynLocalPropertiesUri, brooklynLocalPropertiesRemotePath);
        }

        // Override the ~/.brooklyn/catalog.xml if required
        if (brooklynCatalogContents != null || brooklynCatalogUri != null) {
            uploadFileContents(brooklynCatalogContents, brooklynCatalogUri, brooklynCatalogRemotePath);
        }

        // Copy additional resources to the server
        for (Map.Entry<String,String> entry : getEntity().getAttribute(BrooklynNode.COPY_TO_RUNDIR).entrySet()) {
            Map<String, String> substitutions = ImmutableMap.of("RUN", getRunDir());
            String localResource = entry.getKey();
            String remotePath = entry.getValue();
            String resolvedRemotePath = remotePath;
            for (Map.Entry<String,String> substitution : substitutions.entrySet()) {
                String key = substitution.getKey();
                String val = substitution.getValue();
                resolvedRemotePath = resolvedRemotePath.replace("${"+key+"}", val).replace("$"+key, val);
            }
            machine.copyTo(MutableMap.of("permissions", "0600"), resource.getResourceFromUrl(localResource), resolvedRemotePath);
        }

        for (Object entry : getEntity().getClasspath()) {
            String filename = null;
            String url = null;

            if (entry instanceof String) {
                url = (String) entry;
            } else {
                if (entry instanceof Map) {
                    url = (String) ((Map) entry).get("url");
                    filename = (String) ((Map) entry).get("filename");
                }
            }
            checkNotNull(url, "url");

            // If a local folder, then create archive from contents first
            if (Urls.isDirectory(url)) {
                File jarFile = ArchiveBuilder.jar().addDirContentsAt(new File(url), "").create();
                url = jarFile.getAbsolutePath();
            }

            if (filename == null) {
                // Determine filename
                filename = getFilename(url);
            }
            ArchiveUtils.deploy(MutableMap.<String, Object>of(), url, machine, getRunDir(), Os.mergePaths(getRunDir(), "lib", "dropins"), filename);
        }

        String cmd = entity.getConfig(BrooklynNode.EXTRA_CUSTOMIZATION_SCRIPT);
        if (Strings.isNonBlank(cmd)) {
            DynamicTasks.queueIfPossible( SshEffectorTasks.ssh(cmd).summary("Bespoke BrooklynNode customization script")
                .requiringExitCodeZero() )
                .orSubmitAndBlock(getEntity());
        }
    }

    private String getFilename(String url) {
        String destFile = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        destFile = destFile.substring(destFile.lastIndexOf('/') + 1);
        return destFile;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void launch() {
        String app = getEntity().getAttribute(BrooklynNode.APP);
        String locations = getEntity().getAttribute(BrooklynNode.LOCATIONS);
        boolean hasLocalBrooklynProperties = getEntity().getConfig(BrooklynNode.BROOKLYN_LOCAL_PROPERTIES_CONTENTS) != null || getEntity().getConfig(BrooklynNode.BROOKLYN_LOCAL_PROPERTIES_URI) != null;
        String localBrooklynPropertiesPath = processTemplateContents(getEntity().getConfig(BrooklynNode.BROOKLYN_LOCAL_PROPERTIES_REMOTE_PATH));
        InetAddress bindAddress = getEntity().getAttribute(BrooklynNode.WEB_CONSOLE_BIND_ADDRESS);
        InetAddress publicAddress = getEntity().getAttribute(BrooklynNode.WEB_CONSOLE_PUBLIC_ADDRESS);

        String cmd = entity.getConfig(BrooklynNode.LAUNCH_COMMAND);
        if (Strings.isBlank(cmd)) cmd = "./bin/brooklyn";
        cmd = "nohup " + cmd + " launch";
        if (app != null) {
            cmd += " --app "+app;
        }
        if (locations != null) {
            cmd += " --locations "+locations;
        }
        if (entity.getConfig(BrooklynNode.ON_EXISTING_PROPERTIES_FILE)==ExistingFileBehaviour.DO_NOT_USE) {
            cmd += " --noGlobalBrooklynProperties";
        }
        if (hasLocalBrooklynProperties) {
            cmd += " --localBrooklynProperties "+localBrooklynPropertiesPath;
        }
        Integer webPort = null;
        if (getEntity().isHttpProtocolEnabled("http")) {
            webPort = getEntity().getAttribute(BrooklynNode.HTTP_PORT);
            Networking.checkPortsValid(ImmutableMap.of("webPort", webPort));
        } else if (getEntity().isHttpProtocolEnabled("https")) {
            webPort = getEntity().getAttribute(BrooklynNode.HTTPS_PORT);
            Networking.checkPortsValid(ImmutableMap.of("webPort", webPort));
        }
        if (webPort!=null) {
            cmd += " --port "+webPort;
        } else if (getEntity().getEnabledHttpProtocols().isEmpty()) {
            // TODO sensors will probably not work in this mode
            cmd += " --noConsole";
        } else {
            throw new IllegalStateException("Unknown web protocol in "+BrooklynNode.ENABLED_HTTP_PROTOCOLS+" "
                + "("+getEntity().getEnabledHttpProtocols()+"); expecting 'http' or 'https'");
        }
        
        if (bindAddress != null) {
            cmd += " --bindAddress "+bindAddress.getHostAddress();
        }
        if (publicAddress != null) {
            cmd += " --publicAddress "+publicAddress.getHostName();
        }
        if (getEntity().getAttribute(BrooklynNode.NO_WEB_CONSOLE_AUTHENTICATION)) {
            cmd += " --noConsoleSecurity";
        }
        if (Strings.isNonBlank(getEntity().getConfig(BrooklynNode.EXTRA_LAUNCH_PARAMETERS))) {
            cmd += " "+getEntity().getConfig(BrooklynNode.EXTRA_LAUNCH_PARAMETERS);
        }
        cmd += format(" >> %s/console 2>&1 </dev/null &", getRunDir());
        
        log.info("Starting brooklyn on {} using command {}", getMachine(), cmd);
        
        // relies on brooklyn script creating pid file
        newScript(ImmutableMap.of("usePidFile", 
                entity.getConfig(BrooklynNode.LAUNCH_COMMAND_CREATES_PID_FILE) ? false : getPidFile()), 
            LAUNCHING).
            body.append(
                format("export BROOKLYN_CLASSPATH=%s", getRunDir()+"/lib/\"*\""),
                format("export BROOKLYN_HOME=%s", getBrooklynHome()),
                format(cmd)
        ).failOnNonZeroResultCode().execute();
    }

    @Override
    public boolean isRunning() {
        Map<String,String> flags = ImmutableMap.of("usePidFile", getPidFile());
        int result = newScript(flags, CHECK_RUNNING).execute();
        return result == 0;
    }

    @Override
    public void stop() {
        Map<String,String> flags = ImmutableMap.of("usePidFile", getPidFile());
        newScript(flags, STOPPING).execute();
    }

    @Override
    public void kill() {
        Map<String,String> flags = ImmutableMap.of("usePidFile", getPidFile());
        newScript(flags, KILLING).execute();
    }
    
    @Override
    public Map<String, String> getShellEnvironment() {
        Map<String, String> orig = super.getShellEnvironment();
        String origClasspath = orig.get("CLASSPATH");
        String newClasspath = (origClasspath == null ? "" : origClasspath+":") + 
                getRunDir()+"/conf/" + ":" +
                getRunDir()+"/lib/\"*\"";
        Map<String,String> results = new LinkedHashMap<String,String>();
        results.putAll(orig);
        results.put("BROOKLYN_CLASSPATH", newClasspath);
        results.put("BROOKLYN_HOME", getBrooklynHome());
        results.put("RUN", getRunDir());
        return results;
    }
    
    private void uploadFileContents(String contents, String alternativeUri, String remotePath) {
        checkNotNull(remotePath, "remotePath");
        SshMachineLocation machine = getMachine();
        String tempRemotePath = String.format("%s/upload.tmp", getRunDir());

        if (contents == null && alternativeUri == null) {
            throw new IllegalStateException("No contents supplied for file " + remotePath);
        }
        InputStream stream = contents != null
                ? new ByteArrayInputStream(contents.getBytes())
                : resource.getResourceFromUrl(alternativeUri);
        Map<String, String> flags = MutableMap.of(SshTool.PROP_PERMISSIONS.getName(), "0600");
        machine.copyTo(flags, stream, tempRemotePath);
        newScript(CUSTOMIZING)
                .failOnNonZeroResultCode()
                .body.append(
                        format("mkdir -p %s", remotePath.subSequence(0, remotePath.lastIndexOf("/"))),
                        format("cp -p %s %s", tempRemotePath, remotePath),
                        format("rm -f %s", tempRemotePath))
                .execute();
    }
}
