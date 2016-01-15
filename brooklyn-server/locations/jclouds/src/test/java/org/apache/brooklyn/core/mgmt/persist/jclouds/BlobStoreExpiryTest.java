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
package org.apache.brooklyn.core.mgmt.persist.jclouds;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.jclouds.openstack.reference.AuthHeaders.URL_SUFFIX;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map.Entry;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.http.HttpToolResponse;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.apache.http.client.HttpClient;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.domain.Credentials;
import org.jclouds.openstack.domain.AuthenticationResponse;
import org.jclouds.openstack.reference.AuthHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.apache.brooklyn.location.jclouds.JcloudsUtil;

import com.google.common.base.Preconditions;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.inject.Inject;

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

    private String identity;
    private String credential;
    private String provider;
    private String endpoint;

    public synchronized BlobStoreContext getSwiftBlobStoreContext() {
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

            context = JcloudsUtil.newBlobstoreContext(provider, endpoint, identity, credential);
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

    public void testRenewAuthSucceedsInSwiftObjectStore() throws Exception {
        doTestRenewAuth();
    }
    
    protected void doTestRenewAuth() throws Exception {
        getSwiftBlobStoreContext();
        
        injectShortLivedTokenForSwiftAuth();
        
        context.getBlobStore().createContainerInLocation(null, testContainerName);
        
        assertContainerFound();
        
        log.info("created container, now sleeping for expiration");
        
        Time.sleep(Duration.TEN_SECONDS);
        
        assertContainerFound();

        context.getBlobStore().deleteContainer(testContainerName);
    }

    private void assertContainerFound() {
        PageSet<? extends StorageMetadata> ps = context.getBlobStore().list();
        BlobStoreTest.assertHasItemNamed(ps, testContainerName);
    }

    private void injectShortLivedTokenForSwiftAuth() throws Exception {
        URL endpointUrl = new URL(endpoint);

        HttpToolResponse tokenHttpResponse1 = requestTokenWithExplicitLifetime(endpointUrl,
            identity, credential, Duration.FIVE_SECONDS);
        
        Builder<String, URI> servicesMapBuilder = ImmutableMap.builder();
        for (Entry<String, List<String>> entry : tokenHttpResponse1.getHeaderLists().entrySet()) {
            if (entry.getKey().toLowerCase().endsWith(URL_SUFFIX.toLowerCase()) ||
                    entry.getKey().toLowerCase().endsWith("X-Auth-Token-Expires".toLowerCase())){
                servicesMapBuilder.put(entry.getKey(), URI.create(entry.getValue().iterator().next()));
            }
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
    
    public static HttpToolResponse requestTokenWithExplicitLifetime(URL url, String user, String key, Duration expiration) throws URISyntaxException {
        HttpClient client = HttpTool.httpClientBuilder().build();
        HttpToolResponse response = HttpTool.httpGet(client, url.toURI(), MutableMap.<String,String>of()
            .add(AuthHeaders.AUTH_USER, user)
            .add(AuthHeaders.AUTH_KEY, key)
            .add("Host", url.getHost())
            .add("X-Auth-New-Token", "" + true)
            .add("X-Auth-Token-Lifetime", "" + expiration.toSeconds())
            );
//        curl -v https://ams01.objectstorage.softlayer.net/auth/v1.0/v1.0 -H "X-Auth-User: IBMOS12345-2:username" -H "X-Auth-Key: <API KEY>" -H "Host: ams01.objectstorage.softlayer.net" -H "X-Auth-New-Token: true" -H "X-Auth-Token-Lifetime: 15"
        log.info("Requested token with explicit lifetime: "+expiration+" at "+url+"\n"+response+"\n"+response.getHeaderLists());
        return response;
    }
    
}
