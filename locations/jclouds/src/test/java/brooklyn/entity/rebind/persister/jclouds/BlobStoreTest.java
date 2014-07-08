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

import java.io.IOException;

import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsUtil;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Preconditions;

@Test(groups={"Live", "Live-sanity"})
public class BlobStoreTest {

    /**
     * Live tests as written require a location defined as follows:
     * 
     * <code>
     * brooklyn.location.named.brooklyn-jclouds-objstore-test-1==jclouds:swift:https://ams01.objectstorage.softlayer.net/auth/v1.0
     * brooklyn.location.named.brooklyn-jclouds-objstore-test-1.identity=IBMOS1234-5:yourname
     * brooklyn.location.named.brooklyn-jclouds-objstore-test-1.credential=0123abcd.......
     * </code>
     */
    public static final String PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC = "named:brooklyn-jclouds-objstore-test-1";
    
    public static final String CONTAINER_PREFIX = "brooklyn-persistence-test";
    
    private String locationSpec = PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC;
    
    private JcloudsLocation location;
    private BlobStoreContext context;

    private ManagementContext mgmt;
    private String testContainerName;

    public synchronized BlobStoreContext getBlobStoreContext() {
        if (context==null) {
            if (location==null) {
                Preconditions.checkNotNull(locationSpec, "locationSpec required for remote object store when location is null");
                Preconditions.checkNotNull(mgmt, "mgmt required for remote object store when location is null");
                location = (JcloudsLocation) mgmt.getLocationRegistry().resolve(locationSpec);
            }
            
            String identity = checkNotNull(location.getConfig(LocationConfigKeys.ACCESS_IDENTITY), "identity must not be null");
            String credential = checkNotNull(location.getConfig(LocationConfigKeys.ACCESS_CREDENTIAL), "credential must not be null");
            String provider = checkNotNull(location.getConfig(LocationConfigKeys.CLOUD_PROVIDER), "provider must not be null");
            String endpoint = location.getConfig(CloudLocationConfig.CLOUD_ENDPOINT);

            context = JcloudsUtil.newBlobstoreContext(provider, endpoint, identity, credential, true);
        }
        return context;
    }
    
    @BeforeMethod(alwaysRun=true)
    public void setup() {
        testContainerName = CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(8);
        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
        getBlobStoreContext();
    }
    
    @AfterMethod(alwaysRun=true)
    public void teardown() {
        Entities.destroyAll(mgmt);
    }
    
    public void testCreateListDestroyContainer() throws IOException {
        context.getBlobStore().createContainerInLocation(null, testContainerName);
        context.getBlobStore().list(testContainerName);
        PageSet<? extends StorageMetadata> ps = context.getBlobStore().list();
        assertHasItemNamed(ps, testContainerName);
        
        Blob b = context.getBlobStore().blobBuilder("my-blob-1").payload(Streams.newInputStreamWithContents("hello world")).build();
        context.getBlobStore().putBlob(testContainerName, b);
        
        Blob b2 = context.getBlobStore().getBlob(testContainerName, "my-blob-1");
        Assert.assertEquals(Streams.readFullyString(b2.getPayload().openStream()), "hello world");
        
        context.getBlobStore().deleteContainer(testContainerName);
    }
    
    public void testCreateListDestroySimpleDirInContainer() throws IOException {
        context.getBlobStore().createContainerInLocation(null, testContainerName);
        context.getBlobStore().createDirectory(testContainerName, "my-dir-1");
        
        PageSet<? extends StorageMetadata> ps = context.getBlobStore().list(testContainerName);
        assertHasItemNamed(ps, "my-dir-1");
        
        Blob b = context.getBlobStore().blobBuilder("my-blob-1").payload(Streams.newInputStreamWithContents("hello world")).build();
        context.getBlobStore().putBlob(testContainerName+"/"+"my-dir-1", b);
        
        ps = context.getBlobStore().list(testContainerName, ListContainerOptions.Builder.inDirectory("my-dir-1"));
        assertHasItemNamed(ps, "my-dir-1/my-blob-1");

        // both these syntaxes work:
        Blob b2 = context.getBlobStore().getBlob(testContainerName+"/"+"my-dir-1", "my-blob-1");
        Assert.assertEquals(Streams.readFullyString(b2.getPayload().openStream()), "hello world");

        Blob b3 = context.getBlobStore().getBlob(testContainerName, "my-dir-1"+"/"+"my-blob-1");
        Assert.assertEquals(Streams.readFullyString(b3.getPayload().openStream()), "hello world");

        context.getBlobStore().deleteContainer(testContainerName);
    }

    static void assertHasItemNamed(PageSet<? extends StorageMetadata> ps, String name) {
        if (!hasItemNamed(ps, name))
            Assert.fail("No item named '"+name+"' in "+ps);
    }

    static boolean hasItemNamed(PageSet<? extends StorageMetadata> ps, String name) {
        for (StorageMetadata sm: ps)
            if (name==null || name.equals(sm.getName())) return true;
        return false;
    }

}
