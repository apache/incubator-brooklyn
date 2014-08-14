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
package brooklyn.util.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.os.Os;
import brooklyn.util.stream.StreamGobbler;
import brooklyn.util.stream.Streams;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

public class FileUtil {

    private static final Logger LOG = LoggerFactory.getLogger(FileUtil.class);

    private static boolean loggedSetFilePermissionsWarning = false;
    
    // When we move to java 7, we can use Files.setPosixFilePermissions
    public static void setFilePermissionsTo700(File file) throws IOException {
        file.createNewFile();
        boolean setRead = file.setReadable(false, false) & file.setReadable(true, true);
        boolean setWrite = file.setWritable(false, false) & file.setWritable(true, true);
        boolean setExec = file.setExecutable(false, false) & file.setExecutable(true, true);
        
        if (setRead && setWrite && setExec) {
            if (LOG.isTraceEnabled()) LOG.trace("Set permissions to 700 for file {}", file.getAbsolutePath());
        } else {
            if (loggedSetFilePermissionsWarning) {
                if (LOG.isTraceEnabled()) LOG.trace("Failed to set permissions to 700 for file {}: setRead={}, setWrite={}, setExecutable={}",
                        new Object[] {file.getAbsolutePath(), setRead, setWrite, setExec});
            } else {
                if (Os.isMicrosoftWindows()) {
                    if (LOG.isDebugEnabled()) LOG.debug("Failed to set permissions to 700 for file {}; expected behaviour on Windows; setRead={}, setWrite={}, setExecutable={}; subsequent failures (on any file) will be logged at trace",
                            new Object[] {file.getAbsolutePath(), setRead, setWrite, setExec});
                } else {
                    LOG.warn("Failed to set permissions to 700 for file {}: setRead={}, setWrite={}, setExecutable={}; subsequent failures (on any file) will be logged at trace",
                            new Object[] {file.getAbsolutePath(), setRead, setWrite, setExec});
                }
                loggedSetFilePermissionsWarning = true;
            }
        }
    }
    
    // When we move to java 7, we can use Files.setPosixFilePermissions
    public static void setFilePermissionsTo600(File file) throws IOException {
        file.createNewFile();
        file.setExecutable(false, false);
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
        
        boolean setRead = file.setReadable(false, false) & file.setReadable(true, true);
        boolean setWrite = file.setWritable(false, false) & file.setWritable(true, true);
        boolean setExec = file.setExecutable(false, false);
        
        if (setRead && setWrite && setExec) {
            if (LOG.isTraceEnabled()) LOG.trace("Set permissions to 600 for file {}", file.getAbsolutePath());
        } else {
            if (loggedSetFilePermissionsWarning) {
                if (LOG.isTraceEnabled()) LOG.trace("Failed to set permissions to 600 for file {}: setRead={}, setWrite={}, setExecutable={}",
                        new Object[] {file.getAbsolutePath(), setRead, setWrite, setExec});
            } else {
                if (Os.isMicrosoftWindows()) {
                    if (LOG.isDebugEnabled()) LOG.debug("Failed to set permissions to 600 for file {}; expected behaviour on Windows; setRead={}, setWrite={}, setExecutable={}; subsequent failures (on any file) will be logged at trace",
                            new Object[] {file.getAbsolutePath(), setRead, setWrite, setExec});
                } else {
                    LOG.warn("Failed to set permissions to 600 for file {}: setRead={}, setWrite={}, setExecutable={}; subsequent failures (on any file) will be logged at trace",
                            new Object[] {file.getAbsolutePath(), setRead, setWrite, setExec});
                }
                loggedSetFilePermissionsWarning = true;
            }
        }
    }
    
    public static void moveDir(File srcDir, File destDir) throws IOException, InterruptedException {
        if (!Os.isMicrosoftWindows()) {
            String cmd = "mv '"+srcDir.getAbsolutePath()+"' '"+destDir.getAbsolutePath()+"'";
            Process proc = Runtime.getRuntime().exec(cmd);
            proc.waitFor();
            if (proc.exitValue() == 0) return;
        }
        
        FileUtils.moveDirectory(srcDir, destDir);
    }
    
    public static void copyDir(File srcDir, File destDir) throws IOException, InterruptedException {
        if (!Os.isMicrosoftWindows()) {
            String cmd = "cp -R '"+srcDir.getAbsolutePath()+"' '"+destDir.getAbsolutePath()+"'";
            Process proc = Runtime.getRuntime().exec(cmd);
            proc.waitFor();
            if (proc.exitValue() == 0) return;
        }
        
        FileUtils.copyDirectory(srcDir, destDir);
    }
    
    /**
     * This utility will be deleted when we move to Java 7
     * 
     * @return The file permission (in a form like "-rwxr--r--"), or null if the permissions could not be determined.
     */
    @Beta
    public static Maybe<String> getFilePermissions(File file) {
        if (!file.exists()) return Maybe.absent("File "+file+" does not exist");
        
        if (Os.isMicrosoftWindows()) {
            return Maybe.absent("Cannot determine permissions on windows");
        } else {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int exitcode = exec(ImmutableList.of("ls", "-ld", file.getAbsolutePath()), out, err);
            if (exitcode != 0) {
                if (LOG.isDebugEnabled()) LOG.debug("Could not determine permissions of file "+file+"; exit code "+exitcode+"; stderr "+new String(err.toByteArray()));
                return Maybe.absent("Could not determine permission of file "+file+"; exit code "+exitcode);
            }
            String stdout = new String(out.toByteArray());
            return (stdout.trim().isEmpty() ? Maybe.<String>absent("empty output") : Maybe.of(stdout.split("\\s")[0]));
        }
    }
    
    private static int exec(List<String> cmds, OutputStream out, OutputStream err) {
        StreamGobbler errgobbler = null;
        StreamGobbler outgobbler = null;
        
        ProcessBuilder pb = new ProcessBuilder(cmds);
        
        try {
            Process p = pb.start();
            
            if (out != null) {
                InputStream outstream = p.getInputStream();
                outgobbler = new StreamGobbler(outstream, out, (Logger) null);
                outgobbler.start();
            }
            if (err != null) {
                InputStream errstream = p.getErrorStream();
                errgobbler = new StreamGobbler(errstream, err, (Logger) null);
                errgobbler.start();
            }
            
            int result = p.waitFor();
            
            if (outgobbler != null) outgobbler.blockUntilFinished();
            if (errgobbler != null) errgobbler.blockUntilFinished();
            
            return result;
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        } finally {
            Streams.closeQuietly(outgobbler);
            Streams.closeQuietly(errgobbler);
        }
    }
}
