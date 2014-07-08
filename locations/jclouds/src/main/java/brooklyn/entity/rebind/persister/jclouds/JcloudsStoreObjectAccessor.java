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
package brooklyn.entity.rebind.persister.jclouds;

import java.io.IOException;
import java.util.Date;

import org.apache.commons.io.Charsets;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.util.Strings2;

import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Throwables;
import com.google.common.io.ByteSource;

/**
 * @author Andrea Turli
 */
public class JcloudsStoreObjectAccessor implements PersistenceObjectStore.StoreObjectAccessor {

    private final BlobStore blobStore;
    private final String containerName;
    private final String blobName;

    public JcloudsStoreObjectAccessor(BlobStore blobStore, String containerName, String blobNameOptionallyWithPath) {
        this.blobStore = blobStore;
        this.containerName = containerName;
        this.blobName = blobNameOptionallyWithPath;
    }

    @Override
    public boolean exists() {
        return blobStore.blobExists(containerName, blobName);
    }

    @Override
    public void put(String val) {
        if (val==null) val = "";
        
        blobStore.createContainerInLocation(null, containerName);
        // seems not needed, at least not w SoftLayer
//        blobStore.createDirectory(containerName, directoryName);
        ByteSource payload = ByteSource.wrap(val.getBytes(Charsets.UTF_8));
        Blob blob;
        try {
            blob = blobStore.blobBuilder(blobName).payload(payload)
                    .contentLength(payload.size())
                    .build();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        blobStore.putBlob(containerName, blob);
    }

    @Override
    public void append(String val) {
        String val0 = get();
        if (val0==null) val0="";
        if (val==null) val="";
        put(val0+val);
    }

    @Override
    public void delete() {
        blobStore.removeBlob(containerName, blobName);
    }

    @Override
    public String get() {
        try {
            Blob blob = blobStore.getBlob(containerName, blobName);
            if (blob==null) return null;
            return Strings2.toString(blob.getPayload());
        } catch (IOException e) {
            Exceptions.propagateIfFatal(e);
            throw new IllegalStateException("Error reading blobstore "+containerName+" "+blobName+": "+e, e);
        }
    }

    @Override
    public Date getLastModifiedDate() {
        Blob blob = blobStore.getBlob(containerName, blobName);
        if (blob==null) return null;
        return blob.getMetadata().getLastModified();
    }
    
}
