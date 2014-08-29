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
package brooklyn.entity.nosql.couchbase;

import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class CouchbaseSyncGatewayEc2LiveTest extends AbstractEc2LiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
        CouchbaseCluster cluster = app.createAndManageChild(EntitySpec.create(CouchbaseCluster.class)
            .configure(CouchbaseNode.COUCHBASE_ADMIN_USERNAME, "Administrator")
            .configure(CouchbaseNode.COUCHBASE_ADMIN_PASSWORD, "Password")
            .configure(DynamicCluster.INITIAL_SIZE, 3)
            .configure(CouchbaseCluster.CREATE_BUCKETS, (List<Map<String,Object>>)ImmutableList.of(
                (Map<String,Object>)ImmutableMap.<String, Object>of(
                    "bucket", "default",
                    "bucket-ramsize", 100,
                    "bucket-type", "couchbase",
                    "bucket-port", 11211
                ),
                (Map<String,Object>)ImmutableMap.<String, Object>of(
                    "bucket", "my_bucket",
                    "bucket-ramsize", 100,
                    "bucket-type", "couchbase",
                    "bucket-port", 11223
                ),
                (Map<String,Object>)ImmutableMap.<String, Object>of(
                    "bucket", "another",
                    "bucket-ramsize", 100,
                    "bucket-type", "couchbase",
                    "bucket-port", 11224
                ))
            )
        );
        CouchbaseSyncGateway gateway = app.createAndManageChild(EntitySpec.create(CouchbaseSyncGateway.class)
            .configure(CouchbaseSyncGateway.COUCHBASE_SERVER, cluster)
            .configure(CouchbaseSyncGateway.COUCHBASE_SERVER_BUCKET, "my_bucket")
        );
        
        app.start(ImmutableList.of(loc));
        
        EntityTestUtils.assertAttributeEqualsEventually(gateway, Startable.SERVICE_UP, true);
    }
    
    
    // Supported operating systems
    @Test(groups = {"Live"})
    @Override
    public void test_Ubuntu_12_0() throws Exception {
        super.test_Ubuntu_12_0();
    }
    
    @Test(groups = {"Live"})
    @Override
    public void test_Red_Hat_Enterprise_Linux_6() throws Exception {
        super.test_Red_Hat_Enterprise_Linux_6();
    }
    
    @Test(groups = {"Live"})
    @Override
    public void test_CentOS_6_3() throws Exception {
        super.test_CentOS_6_3();
    }
    
    // Unsupported operating systems
    
    @Test(groups = {"Live"})
    @Override
    public void test_CentOS_5_6() throws Exception {
        // Unsupported
        // error: Failed dependencies:
        //     libc.so.6(GLIBC_2.7)(64bit) is needed by couchbase-server-2.5.1-1083.x86_64
        //        libcrypto.so.10()(64bit) is needed by couchbase-server-2.5.1-1083.x86_64
        //        libreadline.so.6()(64bit) is needed by couchbase-server-2.5.1-1083.x86_64
        //        libssl.so.10()(64bit) is needed by couchbase-server-2.5.1-1083.x86_64
        //        libstdc++.so.6(GLIBCXX_3.4.10)(64bit) is needed by couchbase-server-2.5.1-1083.x86_64
        //        libstdc++.so.6(GLIBCXX_3.4.11)(64bit) is needed by couchbase-server-2.5.1-1083.x86_64
        //        libstdc++.so.6(GLIBCXX_3.4.9)(64bit) is needed by couchbase-server-2.5.1-1083.x86_64
        //        libtinfo.so.5()(64bit) is needed by couchbase-server-2.5.1-1083.x86_64
        //        openssl >= 1.0.0 is needed by couchbase-server-2.5.1-1083.x86_64
        //        rpmlib(FileDigests) <= 4.6.0-1 is needed by couchbase-server-2.5.1-1083.x86_64
        //        rpmlib(PayloadIsXz) <= 5.2-1 is needed by couchbase-server-2.5.1-1083.x86_64
    }
    
    @Test(groups = {"Live"})
    @Override
    public void test_Debian_6() throws Exception {
        // Unsupported
    }
    
    @Test(groups = {"Live"})
    @Override
    public void test_Debian_7_2() throws Exception {
        // Unsupported
    }
    
    @Test(groups = {"Live"})
    @Override
    public void test_Ubuntu_10_0() throws Exception {
        // Unsupported
        // Installing cannot proceed since the package 'libssl1*' is missing. 
        // Please install libssl1* and try again. 
        //    $sudo apt-get install libssl1*
        //
        // Installing libssl1* doesn't fix the issue
    }
}
