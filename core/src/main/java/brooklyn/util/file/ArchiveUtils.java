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
package brooklyn.util.file;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.StackTraceSimplifier;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.task.ssh.SshTasks;
import brooklyn.util.text.Strings;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;

public class ArchiveUtils {

    private static final Logger log = LoggerFactory.getLogger(ArchiveUtils.class);

    // TODO Make this a ConfigKey on the machine location
    /** Number of attempts when copying a file to a remote server. */
    public static final int NUM_RETRIES_FOR_COPYING = 5;

    /**
     * The types of archive that are supported by Brooklyn.
     */
    public static enum ArchiveType {
        TAR,
        TGZ,
        TBZ,
        ZIP,
        JAR,
        WAR,
        EAR,
        UNKNOWN;

        /**
         * Zip format archives used by Java.
         */
        public static Set<ArchiveType> ZIP_ARCHIVES = EnumSet.of(ArchiveType.ZIP, ArchiveType.JAR, ArchiveType.WAR, ArchiveType.EAR);

        public static ArchiveUtils.ArchiveType of(String filename) {
            if (filename == null) return null;
            String ext = Files.getFileExtension(filename);
            try {
                return valueOf(ext.toUpperCase());
            } catch (IllegalArgumentException iae) {
                if (filename.toLowerCase().endsWith(".tar.gz")) {
                    return TGZ;
                } else if (filename.toLowerCase().endsWith(".tar.bz") ||
                        filename.toLowerCase().endsWith(".tar.bz2") ||
                        filename.toLowerCase().endsWith(".tar.xz")) {
                    return TBZ;
                } else {
                    return UNKNOWN;
                }
            }
        }

        @Override
        public String toString() {
            if (UNKNOWN.equals(this)) {
                return "";
            } else {
                return name().toLowerCase();
            }
        }
    }

    /**
     * Returns the list of commands used to install support for an archive with the given name.
     */
    public static List<String> installCommands(String fileName) {
        List<String> commands = new LinkedList<String>();
        switch (ArchiveType.of(fileName)) {
            case TAR:
            case TGZ:
            case TBZ:
                commands.add(BashCommands.INSTALL_TAR);
                break;
            case ZIP:
                commands.add(BashCommands.INSTALL_UNZIP);
                break;
            case JAR:
            case WAR:
            case EAR:
            case UNKNOWN:
                break;
        }
        return commands;
    }

    /**
     * Returns the list of commands used to extract the contents of the archive with the given name.
     * <p>
     * Optionally, Java archives of type
     *
     * @see #extractCommands(String, String)
     */
    public static List<String> extractCommands(String fileName, String sourceDir, String targetDir, boolean extractJar) {
        return extractCommands(fileName, sourceDir, targetDir, extractJar, true);
    }
    
    /** as {@link #extractCommands(String, String, String, boolean)}, but also with option to keep the original */
    public static List<String> extractCommands(String fileName, String sourceDir, String targetDir, boolean extractJar, boolean keepOriginal) {
        List<String> commands = new LinkedList<String>();
        commands.add("cd " + targetDir);
        String sourcePath = Os.mergePathsUnix(sourceDir, fileName);
        switch (ArchiveType.of(fileName)) {
            case TAR:
                commands.add("tar xvf " + sourcePath);
                break;
            case TGZ:
                commands.add("tar xvfz " + sourcePath);
                break;
            case TBZ:
                commands.add("tar xvfj " + sourcePath);
                break;
            case ZIP:
                commands.add("unzip " + sourcePath);
                break;
            case JAR:
            case WAR:
            case EAR:
                if (extractJar) {
                    commands.add("jar -xvf " + sourcePath);
                    break;
                }
            case UNKNOWN:
                if (!sourcePath.equals(Urls.mergePaths(targetDir, fileName)))
                    commands.add("cp " + sourcePath + " " + targetDir);
                break;
        }
        if (!keepOriginal && !commands.isEmpty())
            commands.add("rm "+sourcePath);
        return commands;
    }

    /**
     * Returns the list of commands used to extract the contents of the archive with the given name.
     * <p>
     * The archive will be extracted in its current directory unless it is a Java archive of type {@code .jar},
     * {@code .war} or {@code .ear}, which will be left as is.
     *
     * @see #extractCommands(String, String, String, boolean)
     */
    public static List<String> extractCommands(String fileName, String sourceDir) {
        return extractCommands(fileName, sourceDir, ".", false);
    }

    /**
     * Deploys an archive file to a remote machine and extracts the contents.
     */
    public static void deploy(String archiveUrl, SshMachineLocation machine, String destDir) {
        deploy(MutableMap.<String, Object>of(), archiveUrl, machine, destDir);
    }

    /**
     * Deploys an archive file to a remote machine and extracts the contents.
     * <p>
     * Copies the archive file from the given URL to the destination directory and extracts
     * the contents. If the URL is a local directory, the contents are packaged as a Zip archive first.
     *
     * @see #deploy(String, SshMachineLocation, String, String)
     * @see #deploy(Map, String, SshMachineLocation, String, String, String)
     */
    public static void deploy(Map<String, ?> props, String archiveUrl, SshMachineLocation machine, String destDir) {
        if (Urls.isDirectory(archiveUrl)) {
            File zipFile = ArchiveBuilder.zip().entry(".", Urls.toFile(archiveUrl)).create();
            archiveUrl = zipFile.getAbsolutePath();
        }

        // Determine filename
        String destFile = archiveUrl.contains("?") ? archiveUrl.substring(0, archiveUrl.indexOf('?')) : archiveUrl;
        destFile = destFile.substring(destFile.lastIndexOf('/') + 1);

        deploy(props, archiveUrl, machine, destDir, destFile);
    }

    /**
     * Deploys an archive file to a remote machine and extracts the contents.
     * <p>
     * Copies the archive file from the given URL to a file in the destination directory and extracts
     * the contents.
     *
     * @see #deploy(String, SshMachineLocation, String)
     * @see #deploy(Map, String, SshMachineLocation, String, String, String)
     */
    public static void deploy(String archiveUrl, SshMachineLocation machine, String destDir, String destFile) {
        deploy(MutableMap.<String, Object>of(), archiveUrl, machine, destDir, destDir, destFile);
    }
    public static void deploy(Map<String, ?> props, String archiveUrl, SshMachineLocation machine, String destDir, String destFile) {
        deploy(props, archiveUrl, machine, destDir, destDir, destFile);
    }
    public static void deploy(Map<String, ?> props, String archiveUrl, SshMachineLocation machine, String tmpDir, String destDir, String destFile) {
        deploy(null, props, archiveUrl, machine, destDir, true, tmpDir, destFile);
    }
    
    /**
     * Deploys an archive file to a remote machine and extracts the contents.
     * <p>
     * Copies the archive file from the given URL to a file in a temporary directory and extracts
     * the contents in the destination directory. For Java archives of type {@code .jar},
     * {@code .war} or {@code .ear} the file is simply copied.
     *
     * @see #deploy(String, SshMachineLocation, String)
     * @see #deploy(Map, String, SshMachineLocation, String, String, String)
     * @see #install(SshMachineLocation, String, String, int)
     */
    public static void deploy(ResourceUtils resolver, Map<String, ?> props, String archiveUrl, SshMachineLocation machine, String destDir, boolean keepArchiveAfterUnpacking, String optionalTmpDir, String optionalDestFile) {
        if (optionalDestFile==null) optionalDestFile = Urls.getBasename(Preconditions.checkNotNull(archiveUrl, "archiveUrl"));
        if (Strings.isBlank(optionalDestFile)) 
            throw new IllegalStateException("Not given filename and cannot infer archive type from '"+archiveUrl+"'");
        if (optionalTmpDir==null) optionalTmpDir=Preconditions.checkNotNull(destDir, "destDir");
        if (props==null) props = MutableMap.of();
        String destPath = Os.mergePaths(optionalTmpDir, optionalDestFile);

        // Use the location mutex to prevent package manager locking issues
        try {
            machine.acquireMutex("installing", "installing archive");
            int result = install(resolver, props, machine, archiveUrl, destPath, NUM_RETRIES_FOR_COPYING);
            if (result != 0) {
                throw new IllegalStateException(format("Unable to install archive %s to %s", archiveUrl, machine));
            }
            
            // extract, now using task if available
            MutableList<String> commands = MutableList.copyOf(installCommands(optionalDestFile))
                .appendAll(extractCommands(optionalDestFile, optionalTmpDir, destDir, false, keepArchiveAfterUnpacking));
            if (DynamicTasks.getTaskQueuingContext()!=null) {
                result = DynamicTasks.queue(SshTasks.newSshExecTaskFactory(machine, commands.toArray(new String[0])).summary("extracting archive").requiringExitCodeZero()).get();
            } else {
                result = machine.execCommands(props, "extracting content", commands);
            }
            if (result != 0) {
                throw new IllegalStateException(format("Failed to expand archive %s on %s", archiveUrl, machine));
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } finally {
            machine.releaseMutex("installing");
        }
    }

    /**
     * Installs a URL onto a remote machine.
     *
     * @see #install(Map, SshMachineLocation, String, String, int)
     */
    public static int install(SshMachineLocation machine, String urlToInstall, String target) {
        return install(MutableMap.<String, Object>of(), machine, urlToInstall, target, NUM_RETRIES_FOR_COPYING);
    }

    /**
     * Installs a URL onto a remote machine.
     *
     * @see #install(SshMachineLocation, String, String)
     * @see SshMachineLocation#installTo(Map, String, String)
     */
    public static int install(Map<String, ?> props, SshMachineLocation machine, String urlToInstall, String target, int numAttempts) {
        return install(null, props, machine, urlToInstall, target, numAttempts);
    }
    
    public static int install(ResourceUtils resolver, Map<String, ?> props, SshMachineLocation machine, String urlToInstall, String target, int numAttempts) {
        if (resolver==null) resolver = ResourceUtils.create(machine);
        Exception lastError = null;
        int retriesRemaining = numAttempts;
        int attemptNum = 0;
        do {
            attemptNum++;
            try {
                Tasks.setBlockingDetails("Installing "+urlToInstall+" at "+machine);
                // TODO would be nice to have this in a task (and the things within it!)
                return machine.installTo(resolver, props, urlToInstall, target);
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                lastError = e;
                String stack = StackTraceSimplifier.toString(e);
                if (stack.contains("net.schmizz.sshj.sftp.RemoteFile.write")) {
                    log.warn("Failed to transfer "+urlToInstall+" to "+machine+", retryable error, attempt "+attemptNum+"/"+numAttempts+": "+e);
                    continue;
                }
                log.warn("Failed to transfer "+urlToInstall+" to "+machine+", not a retryable error so failing: "+e);
                throw Exceptions.propagate(e);
            } finally {
                Tasks.resetBlockingDetails();
            }
        } while (retriesRemaining --> 0);
        throw Exceptions.propagate(lastError);
    }

    /**
     * Copies the entire contents of a file to a String.
     *
     * @see com.google.common.io.Files#toString(File, java.nio.charset.Charset)
     */
    public static String readFullyString(File sourceFile) {
        try {
            return Files.toString(sourceFile, Charsets.UTF_8);
        } catch (IOException ioe) {
            throw Exceptions.propagate(ioe);
        }
    }

}
