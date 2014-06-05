package brooklyn.entity.rebind.persister.jclouds;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import javax.annotation.Nullable;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.management.ManagementContext;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;

/**
 * @author Andrea Turli
 */
public class JcloudsBlobStoreBasedObjectStore implements PersistenceObjectStore {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(JcloudsBlobStoreBasedObjectStore.class);

    private final String containerName;
    
    private String locationSpec;
    private JcloudsLocation location;
    private BlobStoreContext context;

    private ManagementContext mgmt;


    public JcloudsBlobStoreBasedObjectStore(String locationSpec, String containerName) {
        this.locationSpec = locationSpec;
        this.containerName = containerName;
    }
    
    public JcloudsBlobStoreBasedObjectStore(JcloudsLocation location, String containerName) {
        this.location = location;
        this.containerName = containerName;
        getBlobStoreContext();
    }

    public String getSummaryName() {
        return (locationSpec!=null ? locationSpec : location)+":"+getContainerName();
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

            context = ContextBuilder.newBuilder(provider)
                .credentials(identity, credential)
                .endpoint(endpoint)
                .buildView(BlobStoreContext.class);
     
            // TODO do we need to get location from region? can't see the jclouds API.
            // doesn't matter in some places because it's already in the endpoint
//            String region = location.getConfig(CloudLocationConfig.CLOUD_REGION_ID);
            context.getBlobStore().createContainerInLocation(null, getContainerName());
        }
        return context;
    }

    public String getContainerName() {
        return containerName;
    }
    
    @Override
    public void createSubPath(String subPath) {
        checkPrepared();
        context.getBlobStore().createDirectory(getContainerName(), subPath);
    }

    protected void checkPrepared() {
        if (context==null)
            throw new IllegalStateException("object store not prepared");
    }
    
    @Override
    public StoreObjectAccessor newAccessor(String path) {
        checkPrepared();
        return new JcloudsStoreObjectAccessor(context.getBlobStore(), getContainerName(), path);
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
        return FluentIterable.from(context.getBlobStore().list(getContainerName(), ListContainerOptions.Builder.inDirectory(parentSubPath)))
                .transform(new Function<StorageMetadata, String>() {
                    @Override
                    public String apply(@javax.annotation.Nullable StorageMetadata input) {
                        return input.getName();
                    }
                }).toList();
    }

    @Override
    public void backupContents(String parentSubPath, String backupSubPath) {
        // TODO for now assume object store deals with backups
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
                .add("basedir", containerName)
                .toString();
    }
    
    @Override
    public void prepareForUse(ManagementContext mgmt, @Nullable PersistMode persistMode) {
        if (this.mgmt!=null && !this.mgmt.equals(mgmt))
            throw new IllegalStateException("Cannot change mgmt context of "+this);
        this.mgmt = mgmt;
        
        // TODO to prepare for persistMode, see backupContents, and how FileBasedObjectStore does it.
        
        getBlobStoreContext();
    }

    @Override
    public void deleteCompletely() {
        getBlobStoreContext().getBlobStore().deleteContainer(containerName);
    }
    
}
