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
package org.apache.brooklyn.launcher.blueprints;

import org.testng.annotations.Test;

public class CouchbaseBlueprintTest extends AbstractBlueprintTest {

    @Test(groups={"Live"})
    public void testCouchbaseNode() throws Exception {
        runTest("couchbase-node.yaml");
    }

    @Test(groups={"Live"})
    public void testCouchbaseCluster() throws Exception {
        runTest("couchbase-cluster.yaml");
    }

    @Test(groups={"Live"})
    public void testCouchbaseClusterSingleNode() throws Exception {
        runTest("couchbase-cluster-singleNode.yaml");
    }
    
    @Test(groups={"Live"})
    public void testCouchbaseWithPillowfight() throws Exception {
        runTest("couchbase-w-pillowfight.yaml");
    }

    /**
     * FIXME Failed with "Unable to match required VM template constraints" - caused by NPE:
     *   Caused by: java.lang.NullPointerException: id
     *     at com.google.common.base.Preconditions.checkNotNull(Preconditions.java:229)
     *     at org.jclouds.softlayer.domain.OperatingSystem.<init>(OperatingSystem.java:106)
     *     at org.jclouds.softlayer.domain.OperatingSystem$Builder.build(OperatingSystem.java:87)
     *     at org.jclouds.softlayer.domain.ContainerVirtualGuestConfiguration$4.apply(ContainerVirtualGuestConfiguration.java:209)
     *     at org.jclouds.softlayer.domain.ContainerVirtualGuestConfiguration$4.apply(ContainerVirtualGuestConfiguration.java:206)
     * This blueprint uses {minRam: 16384, minCores: 4}.
     * Suspect this is already fixed by Andrea Turli in latest jclouds.
     */
    @Test(groups={"Live", "WIP"})
    public void testCouchbaseWithLoadgen() throws Exception {
        runTest("couchbase-w-loadgen.yaml");
    }

    /**
     * FIXME Failed with "Unable to match required VM template constraints" - caused by NPE
     * (see error described at {@link #testCouchbaseWithLoadgen()}.
     */
    @Test(groups={"Live", "WIP"})
    public void testCouchbaseReplicationWithPillowfight() throws Exception {
        runTest("couchbase-replication-w-pillowfight.yaml");
    }
}
