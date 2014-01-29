/*
 * Copyright 2012-2014 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.util.file;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.StackTraceSimplifier;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class ArchiveUtils {

    private static final Logger log = LoggerFactory.getLogger(ArchiveUtils.class);

    public static final int NUM_RETRIES_FOR_COPYING = 5;

    public static enum ArchiveType {
        TAR,
        TGZ,
        ZIP,
        JAR,
        WAR,
        EAR,
        UNKNOWN;

        public static ArchiveUtils.ArchiveType of(String filename) {
            if (filename == null) return null;
            String ext = Files.getFileExtension(filename);
            try {
                return valueOf(ext.toUpperCase());
            } catch (IllegalArgumentException iae) {
                if (filename.toLowerCase().endsWith(".tar.gz")) {
                    return TGZ;
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

    public static List<String> installCommands(String filename) {
        List<String> commands = new LinkedList<String>();
        switch (ArchiveType.of(filename)) {
            case TAR:
            case TGZ:
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

    public static List<String> extractCommands(String fileName, String sourceDir, String targetDir, boolean extractJar) {
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
                commands.add("cp " + sourcePath + " " + targetDir);
                break;
        }
        return commands;
    }

    public static List<String> extractCommands(String fileName, String sourceDir) {
        return extractCommands(fileName, sourceDir, ".", false);
    }

    /**
     * Deploys an archive file from the given URL to a directory on a remote machine and extracts the contents.
     * <p>
     * If the URL is a local directory, the contents are packaged as a Zip archive first.
     *
     * @see #deploy(String, SshMachineLocation, String, String)
     */
    public static void deploy(String archiveUrl, SshMachineLocation machine, String destDir) {
        if (Urls.isDirectory(archiveUrl)) {
            File zipFile = ArchiveBuilder.zip().add(archiveUrl).create();
            archiveUrl = zipFile.getAbsolutePath();
        }

        // Determine filename
        String destFile = archiveUrl.contains("?") ? archiveUrl.substring(0, archiveUrl.indexOf('?')) : archiveUrl;
        destFile = destFile.substring(destFile.lastIndexOf('/') + 1);

        deploy(archiveUrl, machine, destDir, destFile);
    }

    public static void deploy(String archiveUrl, SshMachineLocation machine, String destDir, String destFile) {
        deploy(MutableMap.<String, Object>of(), archiveUrl, machine, destDir, destDir, destFile);
    }

    /**
     * Deploys an archive file from the given URL to a remote machine and extracts the contents.
     * <p>
     * For Java archives of type Jar, War and Ear, the file is simply copied.
     *
     * @see #install(SshMachineLocation, String, String, int)
     */
    public static void deploy(Map<String, ?> sshProps, String archiveUrl, SshMachineLocation machine, String tmpDir, String destDir, String destFile) {
        String destPath = Os.mergePaths(tmpDir, destFile);

        // Use the location mutex to prevent package manager locking issues
        try {
            machine.acquireMutex("installing", "installing archive");
            int result = install(sshProps, machine, archiveUrl, destPath, NUM_RETRIES_FOR_COPYING);
            if (result != 0) {
                throw new IllegalStateException(format("Unable to install archive %s to %s", archiveUrl, machine));
            }
            result = machine.execCommands(sshProps, "extracting content", extractCommands(destFile, tmpDir, destDir, false));
            if (result != 0) {
                throw new IllegalStateException(format("Failed to expand archive %s on %s", archiveUrl, machine));
            }
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } finally {
            machine.releaseMutex("installing");
        }
    }

    public static int install(SshMachineLocation machine, String urlToInstall, String target) {
        return install(MutableMap.<String, Object>of(), machine, urlToInstall, target, NUM_RETRIES_FOR_COPYING);
    }

    public static int install(Map<String, ?> sshProps, SshMachineLocation machine, String urlToInstall, String target, int numAttempts) {
        Exception lastError = null;
        int retriesRemaining = numAttempts;
        int attemptNum = 0;
        do {
            attemptNum++;
            try {
                return machine.installTo(urlToInstall, target);
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
            }
        } while (retriesRemaining --> 0);
        throw Exceptions.propagate(lastError);
    }

    public static String readFullyString(File sourceFile) {
        try {
            return Files.toString(sourceFile, Charsets.UTF_8);
        } catch (IOException ioe) {
            throw Exceptions.propagate(ioe);
        }
    }

}
