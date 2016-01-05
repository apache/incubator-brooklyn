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
package org.apache.brooklyn.core.mgmt.persist;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.io.FileUtil;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.io.Files;

/**
 * Reads/writes to a file. This impl does it immediately, with no synchronisation.
 * Callers should wrap in {@link StoreObjectAccessorLocking} if multiple threads may be accessing this.
 *
 * @author aled
 */
public class FileBasedStoreObjectAccessor implements PersistenceObjectStore.StoreObjectAccessor {
    private static final Logger LOG = LoggerFactory.getLogger(FileBasedStoreObjectAccessor.class);

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
            throw Exceptions.propagate("Problem reading String contents of file "+file, e);
        }
    }

    @Override
    public byte[] getBytes() {
        try {
            if (!exists()) return null;
            return Files.asByteSource(file).read();
        } catch (IOException e) {
            throw Exceptions.propagate("Problem reading bytes of file "+file, e);
        }
    }

    @Override
    public boolean exists() {
        return file.exists();
    }

    @Override
    public void put(String val) {
        try {
            if (val==null) val = "";
            FileUtil.setFilePermissionsTo600(tmpFile);
            Files.write(val, tmpFile, Charsets.UTF_8);
            FileBasedObjectStore.moveFile(tmpFile, file);
        } catch (IOException e) {
            throw Exceptions.propagate("Problem writing data to file "+file+" (via temporary file "+tmpFile+")", e);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }

    // TODO Should this write to the temporary file? Otherwise we'll risk getting a partial view of the write.
    @Override
    public void append(String val) {
        try {
            if (val==null) val = "";
            FileUtil.setFilePermissionsTo600(file);
            Files.append(val, file, Charsets.UTF_8);
            
        } catch (IOException e) {
            throw Exceptions.propagate("Problem appending to file "+file, e);
        }
    }

    @Override
    public void delete() {
        if (!file.delete()) {
            if (!file.exists()) {
                LOG.debug("Unable to delete " + file.getAbsolutePath() + ". Probably did not exist.");
            } else {
                LOG.warn("Unable to delete " + file.getAbsolutePath() + ". Probably still locked.");
            }
        }
        if (tmpFile.exists() && !tmpFile.delete()) {
            // tmpFile is probably already deleted, so don't even log debug if it does not exist
            LOG.warn("Unable to delete " + tmpFile.getAbsolutePath() + ". Probably still locked.");
        }
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
