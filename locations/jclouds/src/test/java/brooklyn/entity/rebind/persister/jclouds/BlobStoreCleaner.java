package brooklyn.entity.rebind.persister.jclouds;

import static com.google.common.base.Preconditions.checkNotNull;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.test.entity.LocalManagementContextForTests;

/** Utility for cleaning up after test leaks. Most should not leak of course, but if they do... */
public class BlobStoreCleaner {

    private static String locationSpec = BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC;

    private static final Logger log = LoggerFactory.getLogger(BlobStoreCleaner.class);
    
    public static void main(String[] args) {
        LocalManagementContextForTests mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault()); 
        JcloudsLocation location = (JcloudsLocation) mgmt.getLocationRegistry().resolve(locationSpec);
            
        String identity = checkNotNull(location.getConfig(LocationConfigKeys.ACCESS_IDENTITY), "identity must not be null");
        String credential = checkNotNull(location.getConfig(LocationConfigKeys.ACCESS_CREDENTIAL), "credential must not be null");
        String provider = checkNotNull(location.getConfig(LocationConfigKeys.CLOUD_PROVIDER), "provider must not be null");
        String endpoint = location.getConfig(CloudLocationConfig.CLOUD_ENDPOINT);

        BlobStoreContext context = ContextBuilder.newBuilder(provider)
                .credentials(identity, credential)
                .endpoint(endpoint)
                .buildView(BlobStoreContext.class);
        
        PageSet<? extends StorageMetadata> containers = context.getBlobStore().list();
        for (StorageMetadata container: containers) {
            if (container.getName().matches("brooklyn.*-test.*")
                // to kill all containers here
//                || container.getName().matches(".*")
                ) {
                log.info("killing - "+container.getName());
                context.getBlobStore().deleteContainer(container.getName());
            } else {
                log.info("KEEPING - "+container.getName());
            }
        }
        context.close();
    }

}
