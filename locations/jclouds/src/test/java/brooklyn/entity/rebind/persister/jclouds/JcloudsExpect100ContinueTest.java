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

import org.apache.brooklyn.management.ManagementContext;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsUtil;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.text.Identifiers;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;

public class JcloudsExpect100ContinueTest {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(JcloudsExpect100ContinueTest.class);

    private static String LOCATION_SPEC = BlobStoreTest.PERSIST_TO_OBJECT_STORE_FOR_TEST_SPEC;
    private static final String OBJECT_NAME_PUT = "test_put";
    private static final String OBJECT_NAME_GET = "test_get";
    
    private ManagementContext mgmt;
    private BlobStoreContext context;
    private String containerName;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        // It's important to disable jclouds debug logging
        // as it "fixes" the issue.
        setInfoLevel(Logger.ROOT_LOGGER_NAME);
        setInfoLevel("jclouds");
        setInfoLevel("org.jclouds");

        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
        JcloudsLocation jcloudsLocation = (JcloudsLocation) mgmt.getLocationRegistry().resolve(LOCATION_SPEC);
        context = JcloudsUtil.newBlobstoreContext(
                jcloudsLocation.getProvider(),
                jcloudsLocation.getEndpoint(),
                jcloudsLocation.getIdentity(),
                jcloudsLocation.getCredential());
        containerName = BlobStoreTest.CONTAINER_PREFIX+"-"+Identifiers.makeRandomId(8);
        context.getBlobStore().createContainerInLocation(null, containerName);
    }

    private void setInfoLevel(String loggerName) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        logger.setLevel(Level.INFO);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            context.getBlobStore().deleteContainer(containerName);
        } catch (Exception e){}
        context.close();
        Entities.destroyAll(mgmt);
    }
    
    @Test(groups = "Live", timeOut=240000)
    public void testPutAfterUnclosedGet() {
        put(OBJECT_NAME_PUT, getContent());
        put(OBJECT_NAME_GET, getContent());

        for (int i = 1; i <= 50; i++) {
            long start = System.currentTimeMillis();
            get(OBJECT_NAME_GET);
            long afterGet = System.currentTimeMillis();
            LOG.info(i + ". GET @" + (afterGet - start));

            System.gc();
            System.gc();
            System.gc();
            sleep(1000);

            // Without excluding Expect: 100-Continue header
            // the PUT is supposed to block until the server
            // times out

            long beforePut = System.currentTimeMillis();
            put(OBJECT_NAME_PUT, getContent());
            long end = System.currentTimeMillis();
            LOG.info(i + ". PUT @" + (end - beforePut));
        }
    }

    private String getContent() {
        return "1234567890";
    }

    private void put(String name, String content) {
        BlobStore blobStore = context.getBlobStore();
        byte[] bytes = content.getBytes(Charsets.UTF_8);
        Blob blob = blobStore.blobBuilder(name)
                .payload(ByteSource.wrap(bytes))
                .contentLength(bytes.length)
                .build();
        try {
            blobStore.putBlob(containerName, blob);
        } catch (Exception e) {
            LOG.error("PUT " + name + " failed", e);
        }
    }

    private Blob get(String name) {
        try {
            BlobStore blobStore = context.getBlobStore();
            return blobStore.getBlob(containerName, name);
        } catch (Exception e) {
            LOG.error("GET " + name + " failed", e);
            return null;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }

}
