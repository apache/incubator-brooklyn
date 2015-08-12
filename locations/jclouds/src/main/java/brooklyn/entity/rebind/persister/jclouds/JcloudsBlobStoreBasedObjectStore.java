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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.ha.HighAvailabilityMode;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsUtil;
import brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;

/**
 * @author Andrea Turli
 */
public class JcloudsBlobStoreBasedObjectStore implements PersistenceObjectStore {

    private static final Logger log = LoggerFactory.getLogger(JcloudsBlobStoreBasedObjectStore.class);

    private final String containerNameFirstPart;
    private final String containerSubPath;
    
    private String locationSpec;
    private JcloudsLocation location;
    private BlobStoreContext context;

    private ManagementContext mgmt;

    public JcloudsBlobStoreBasedObjectStore(String locationSpec, String containerName) {
        this.locationSpec = locationSpec;
        String[] segments = splitOnce(containerName);
        this.containerNameFirstPart = segments[0];
        this.containerSubPath = segments[1];
    }
    
    private String[] splitOnce(String path) {
        String separator = subPathSeparator();
        int index = path.indexOf(separator);
        if (index<0) return new String[] { path, "" };
        return new String[] { path.substring(0, index), path.substring(index+separator.length()) };
    }

    public JcloudsBlobStoreBasedObjectStore(JcloudsLocation location, String containerName) {
        this.location = location;
        String[] segments = splitOnce(containerName);
        this.containerNameFirstPart = segments[0];
        this.containerSubPath = segments[1];
        getBlobStoreContext();
    }

    public String getSummaryName() {
        return (locationSpec!=null ? locationSpec : location)+":"+getContainerNameFull();
    }
    
    public synchronized BlobStoreContext getBlobStoreContext() {
        if (context==null) {
            if (location==null) {
                Preconditions.checkNotNull(locationSpec, "locationSpec required for remote object store when location is null");
                Preconditions.checkNotNull(mgmt, "mgmt not injected / object store not prepared");
                location = (JcloudsLocation) mgmt.getLocationRegistry().resolve(locationSpec);
            }
            
            String identity = checkNotNull(location.getConfig(LocationConfigKeys.ACCESS_IDENTITY), "identity must not be null");
            String credential = checkNotNull(location.getConfig(LocationConfigKeys.ACCESS_CREDENTIAL), "credential must not be null");
            String provider = checkNotNull(location.getConfig(LocationConfigKeys.CLOUD_PROVIDER), "provider must not be null");
            String endpoint = location.getConfig(CloudLocationConfig.CLOUD_ENDPOINT);

            context = JcloudsUtil.newBlobstoreContext(provider, endpoint, identity, credential);
     
            // TODO do we need to get location from region? can't see the jclouds API.
            // doesn't matter in some places because it's already in the endpoint
//            String region = location.getConfig(CloudLocationConfig.CLOUD_REGION_ID);
            context.getBlobStore().createContainerInLocation(null, getContainerNameFirstPart());
        }
        return context;
    }

    @Override
    public void prepareForMasterUse() {
        // backups not supported here, that is all which is needed for master use
        // that's now normally done *prior* to calling in to here for writes
        // (and we have already thrown in prepareForSharedUse if legacy backups have been specified as required)
    }
    
    public String getContainerName() {
        return getContainerNameFull();
    }
    
    protected String getContainerNameFull() {
        return mergePaths(containerNameFirstPart, containerSubPath);
    }

    protected String getContainerNameFirstPart() {
        return containerNameFirstPart;
    }
    
    protected String getItemInContainerSubPath(String path) {
        if (Strings.isBlank(containerSubPath)) return path;
        return mergePaths(containerSubPath, path);
    }

    @Override
    public void createSubPath(String subPath) {
        // not needed - subpaths are created on demant
        // (and buggy on softlayer w swift w jclouds 1.7.2:
        // throws a "not found" if we're creating an empty directory from scratch)
//        context.getBlobStore().createDirectory(getContainerName(), subPath);
    }

    protected void checkPrepared() {
        if (context==null)
            throw new IllegalStateException("object store not prepared");
    }
    
    @Override
    public StoreObjectAccessor newAccessor(String path) {
        checkPrepared();
        return new JcloudsStoreObjectAccessor(context.getBlobStore(), getContainerNameFirstPart(), getItemInContainerSubPath(path));
    }

    protected String mergePaths(String basePath, String ...subPaths) {
        StringBuilder result = new StringBuilder(basePath);
        for (String subPath: subPaths) {
            if (result.length()>0 && subPath.length()>0) {
                result.append(subPathSeparator());
                result.append(subPath);
            }
        }
        return result.toString();
    }
    
    protected String subPathSeparator() {
        // in case some object stores don't allow / for paths
        return "/";
    }

    @Override
    public List<String> listContentsWithSubPath(final String parentSubPath) {
        checkPrepared();
        return FluentIterable.from(context.getBlobStore().list(getContainerNameFirstPart(), 
            ListContainerOptions.Builder.inDirectory(getItemInContainerSubPath(parentSubPath))))
                .transform(new Function<StorageMetadata, String>() {
                    @Override
                    public String apply(@javax.annotation.Nullable StorageMetadata input) {
                        String result = input.getName();
                        result = Strings.removeFromStart(result, containerSubPath);
                        result = Strings.removeFromStart(result, "/");
                        return result;
                    }
                }).toList();
    }

    @Override
    public void close() {
        if (context!=null)
            context.close();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("blobStoreContext", context)
                .add("basedir", containerNameFirstPart)
                .toString();
    }
    
    @Override
    public void injectManagementContext(ManagementContext mgmt) {
        if (this.mgmt!=null && !this.mgmt.equals(mgmt))
            throw new IllegalStateException("Cannot change mgmt context of "+this);
        this.mgmt = mgmt;
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public void prepareForSharedUse(@Nullable PersistMode persistMode, HighAvailabilityMode haMode) {
        if (mgmt==null) throw new NullPointerException("Must inject ManagementContext before preparing "+this);
        
        getBlobStoreContext();
        
        if (persistMode==null || persistMode==PersistMode.DISABLED) {
            log.warn("Should not be using "+this+" when persistMode is "+persistMode);
            return;
        }
        
        Boolean backups = mgmt.getConfig().getConfig(BrooklynServerConfig.PERSISTENCE_BACKUPS_REQUIRED);
        if (Boolean.TRUE.equals(backups)) {
            log.warn("Using legacy backup for "+this+"; functionality will be removed in future versions, in favor of promotion/demotion-specific backups to a configurable backup location.");
            throw new FatalConfigurationRuntimeException("Backups not supported for object store ("+this+")");
        }
    }

    @Override
    public void deleteCompletely() {
        if (Strings.isBlank(containerSubPath))
            getBlobStoreContext().getBlobStore().deleteContainer(containerNameFirstPart);
        else
            newAccessor(containerSubPath).delete();
    }
    
}
