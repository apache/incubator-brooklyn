/*
 * Copyright 2015 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.brooklyn.util.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import java.util.EnumSet;
import java.util.Set;
import org.apache.brooklyn.util.os.Os;

/**
 * Portable file permissions.
 *
 * @author Ciprian Ciubotariu <cheepeero@gmx.net>
 */
public class FilePermissions {

    private final int posixMode;
    private final Set<PosixFilePermission> posixPermissions = EnumSet.noneOf(PosixFilePermission.class);

    public FilePermissions(int posixMode) {
        this.posixMode = posixMode;
        if ((posixMode & 0400) != 0) posixPermissions.add(OWNER_READ);
        if ((posixMode & 0200) != 0) posixPermissions.add(OWNER_WRITE);
        if ((posixMode & 0100) != 0) posixPermissions.add(OWNER_EXECUTE);
        if ((posixMode & 0040) != 0) posixPermissions.add(GROUP_READ);
        if ((posixMode & 0020) != 0) posixPermissions.add(GROUP_WRITE);
        if ((posixMode & 0010) != 0) posixPermissions.add(GROUP_EXECUTE);
        if ((posixMode & 0004) != 0) posixPermissions.add(OTHERS_READ);
        if ((posixMode & 0002) != 0) posixPermissions.add(OTHERS_WRITE);
        if ((posixMode & 0001) != 0) posixPermissions.add(OTHERS_EXECUTE);
    }

    public void apply(File file) throws IOException {
        Path filePath = file.toPath();

        // the appropriate condition is actually Files.getFileStore(filePath).supportsFileAttributeView(PosixFileAttributeView.class)
        // but that downs performance to ~9000 calls per second

        boolean done = false;
        try {
            // ~59000 calls per sec
            if (!Os.isMicrosoftWindows()) {
                Files.setPosixFilePermissions(filePath, posixPermissions);
            }
            done = true;
        } catch (UnsupportedOperationException ex) {}
        
        if (!done) {
            // ~42000 calls per sec
            // TODO: what happens to group permissions ?
            boolean setRead = file.setReadable(posixPermissions.contains(OTHERS_READ), false) & file.setReadable(posixPermissions.contains(OWNER_READ), true);
            boolean setWrite = file.setWritable(posixPermissions.contains(OTHERS_WRITE), false) & file.setWritable(posixPermissions.contains(OWNER_WRITE), true);
            boolean setExec = file.setExecutable(posixPermissions.contains(OTHERS_EXECUTE), false) & file.setExecutable(posixPermissions.contains(OWNER_EXECUTE), true);

            if (!(setRead && setWrite && setExec)) {
                throw new IOException("setting file permissions failed: read=" + setRead + " write=" + setWrite + " exec=" + setExec);
            }
        }
    }

    @Override
    public String toString() {
        return "posix mode " + Integer.toOctalString(posixMode);
    }

}
