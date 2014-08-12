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
package brooklyn.entity.rebind.persister;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.io.FileUtil;
import brooklyn.util.text.Strings;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

/**
 * Reads/writes to a file. This impl does it immediately, with no synchronisation.
 * Callers should wrap in {@link StoreObjectAccessorLocking} if multiple threads may be accessing this.
 *
 * @author aled
 */
public class FileBasedStoreObjectAccessor implements PersistenceObjectStore.StoreObjectAccessor {

    /**
     * @param file
     * @param executor A sequential executor (e.g. SingleThreadedExecutor, or equivalent)
     */
    public FileBasedStoreObjectAccessor(File file, String tmpExtension) {
        this.file = file;
        this.tmpFile = new File(file.getParentFile(), file.getName()+(Strings.isBlank(tmpExtension) ? ".tmp" : tmpExtension));
    }

    private final File file;
    private final File tmpFile;
    
    @Override
    public String get() {
        try {
            if (!exists()) return null;
            return Files.asCharSource(file, Charsets.UTF_8).read();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public boolean exists() {
        return file.exists();
    }

    // Setting permissions to 600 reduces objectAccessor.put performance from about 5000 per second to 3000 per second
    // in java 6. With Java 7's Files.setPosixFilePermissions, this might well improve.
    @Override
    public void put(String val) {
        try {
            if (val==null) val = "";
            FileUtil.setFilePermissionsTo600(tmpFile);
            Files.write(val, tmpFile, Charsets.UTF_8);
            FileBasedObjectStore.moveFile(tmpFile, file);
            
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public void append(String val) {
        try {
            if (val==null) val = "";
            FileUtil.setFilePermissionsTo600(file);
            Files.append(val, file, Charsets.UTF_8);
            
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public void delete() {
        file.delete();
        tmpFile.delete();
    }

    @Override
    public Date getLastModifiedDate() {
        long result = file.lastModified();
        if (result==0) return null;
        return new Date(result);
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("file", file).toString();
    }
}
