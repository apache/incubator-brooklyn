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
import static org.jclouds.openstack.reference.AuthHeaders.URL_SUFFIX;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map.Entry;

import org.apache.http.client.HttpClient;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.domain.Credentials;
import org.jclouds.openstack.domain.AuthenticationResponse;
import org.jclouds.openstack.handlers.RetryOnRenew;
import org.jclouds.openstack.reference.AuthHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import brooklyn.util.collections.MutableMap;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Preconditions;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.inject.Inject;
import com.google.inject.Module;

@Test(groups={"Live", "Live-sanity"})
public class BlobStoreExpiryTest {

    private static final Logger log = LoggerFactory.getLogger(BlobStoreExpiryTest.class);
    
    /**
     * Live tests as written require a location defined as follows:
     * 
     * <code>
     * brooklyn.location.named.brooklyn-jclouds-objstore-test-1==jclouds:swift:https://ams01.objectstorage.softlayer.net/auth/v1.0
     * brooklyn.location.named.brooklyn-jclouds-objstore-test-1.identity=IBMOS1234-5:yourname
     * brooklyn.location.named.brooklyn-jclouds-objstore-test-1.credential=0123abcd.......
     * </code>
     */
    
    public static final String PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC = BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC;
    public static final String CONTAINER_PREFIX = "brooklyn-persistence-test";
    private String locationSpec = PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC;
    
    private JcloudsLocation location;
    private BlobStoreContext context;

    private ManagementContext mgmt;
    private String testContainerName;

    Module myAuth;
    private String identity;
    private String credential;
    private String provider;
    private String endpoint;

    public synchronized BlobStoreContext getBlobStoreContext(boolean applyFix) {
        if (context==null) {
            if (location==null) {
                Preconditions.checkNotNull(locationSpec, "locationSpec required for remote object store when location is null");
                Preconditions.checkNotNull(mgmt, "mgmt required for remote object store when location is null");
                location = (JcloudsLocation) mgmt.getLocationRegistry().resolve(locationSpec);
            }
            
            identity = checkNotNull(location.getConfig(LocationConfigKeys.ACCESS_IDENTITY), "identity must not be null");
            credential = checkNotNull(location.getConfig(LocationConfigKeys.ACCESS_CREDENTIAL), "credential must not be null");
            provider = checkNotNull(location.getConfig(LocationConfigKeys.CLOUD_PROVIDER), "provider must not be null");
            endpoint = location.getConfig(CloudLocationConfig.CLOUD_ENDPOINT);

            context = JcloudsUtil.newBlobstoreContext(provider, endpoint, identity, credential, applyFix);
        }
        return context;
    }
    
    @BeforeMethod(alwaysRun=true)
    public void setup() {
        testContainerName = CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(8);
        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
    }
    
    @AfterMethod(alwaysRun=true)
    public void teardown() {
        Entities.destroyAll(mgmt);
        if (context!=null) context.close();
        context = null;
    }
    
    public void testRenewAuthFailsInSoftlayer() throws IOException {
        doTestRenewAuth(false);
    }

    public void testRenewAuthSucceedsWithOurOverride() throws IOException {
        doTestRenewAuth(true);
    }
    
    protected void doTestRenewAuth(boolean applyFix) throws IOException {
        getBlobStoreContext(applyFix);
        
        injectShortLivedTokenForSoftlayerAmsterdam();
        
        context.getBlobStore().createContainerInLocation(null, testContainerName);
        
        assertContainerFound();
        
        log.info("created container, now sleeping for expiration");
        
        Time.sleep(Duration.TEN_SECONDS);
        
        if (!applyFix) {
            // with the fix not applied, we have to invalidate the cache manually
            try {
                assertContainerFound();
                Assert.fail("should fail as long as "+RetryOnRenew.class+" is not working");
            } catch (Exception e) {
                log.info("failed, as expected: "+e);
            }
            getAuthCache().invalidateAll();
            log.info("invalidated, should now succeed");
        }

        assertContainerFound();

        context.getBlobStore().deleteContainer(testContainerName);
    }

    private void assertContainerFound() {
        PageSet<? extends StorageMetadata> ps = context.getBlobStore().list();
        BlobStoreTest.assertHasItemNamed(ps, testContainerName);
    }

    private void injectShortLivedTokenForSoftlayerAmsterdam() {
        HttpToolResponse tokenHttpResponse1 = requestTokenWithExplicitLifetime("https://ams01.objectstorage.softlayer.net/auth/v1.0/v1.0", "ams01.objectstorage.softlayer.net", 
            identity, credential, Duration.FIVE_SECONDS);
        
        Builder<String, URI> servicesMapBuilder = ImmutableMap.builder();
        for (Entry<String, List<String>> entry : tokenHttpResponse1.getHeaderLists().entrySet()) {
           if (entry.getKey().toLowerCase().endsWith(URL_SUFFIX.toLowerCase()))
              servicesMapBuilder.put(entry.getKey(), URI.create(entry.getValue().iterator().next()));
        }
        AuthenticationResponse authResponse = new AuthenticationResponse(tokenHttpResponse1.getHeaderLists().get(AuthHeaders.AUTH_TOKEN).get(0), servicesMapBuilder.build());
        
        getAuthCache().put(new Credentials(identity, credential), authResponse);
    }

    private LoadingCache<Credentials, AuthenticationResponse> getAuthCache() {
        return context.utils().injector().getInstance(CachePeeker.class).authenticationResponseCache;
    }
    
    public static class CachePeeker {
        private final LoadingCache<Credentials, AuthenticationResponse> authenticationResponseCache;

        @Inject
        protected CachePeeker(LoadingCache<Credentials, AuthenticationResponse> authenticationResponseCache) {
           this.authenticationResponseCache = authenticationResponseCache;
        }
    }
    
    public static HttpToolResponse requestTokenWithExplicitLifetime(String url, String host, String user, String key, Duration expiration) {
        HttpClient client = HttpTool.httpClientBuilder().build();
        HttpToolResponse response = HttpTool.httpGet(client, URI.create(url), MutableMap.<String,String>of()
            .add(AuthHeaders.AUTH_USER, user)
            .add(AuthHeaders.AUTH_KEY, key)
            .add("Host", host)
            .add("X-Auth-New-Token", ""+true)
            .add("X-Auth-Token-Lifetime", ""+expiration.toSeconds())
            );
//        curl -v https://ams01.objectstorage.softlayer.net/auth/v1.0/v1.0 -H "X-Auth-User: IBMOS321366-2:cloudsoft" -H "X-Auth-Key: 06cef1beff5432cc9453934e06beb85de5f0a53a2340d7e0cd4a4705655e8132" -H "Host: ams01.objectstorage.softlayer.net" -H "X-Auth-New-Token: true" -H "X-Auth-Token-Lifetime: 15"
//            -H "Host: ams01.objectstorage.softlayer.net" -H "X-Auth-New-Token: true" -H "X-Auth-Token-Lifetime: 15"
        log.info("Requested token with explicit lifetime: "+expiration+" at "+url+"\n"+response+"\n"+response.getHeaderLists());
        return response;
    }
    
}
