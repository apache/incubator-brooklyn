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
package org.apache.brooklyn.entity.nosql.couchbase;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.BasicOsDetails;
import org.apache.brooklyn.core.location.BasicOsDetails.OsArchs;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;

public class CouchbaseOfflineTest {

    private LocalManagementContext mgmt;

    @BeforeMethod
    public void setUp() {
        mgmt = LocalManagementContextForTests.newInstance();
    }
    
    @AfterMethod
    public void tearDown() {
        Entities.destroyAll(mgmt);
    }
    
    @Test
    public void testResolvingDownloadLinks() {
        checkOsTag("linux", OsArchs.I386, "unknown", true, "centos6.x86.rpm");
        checkOsTag("linux", OsArchs.I386, "unknown", false, "x86.rpm");
        checkOsTag("rhel", OsArchs.X_86_64, "6", true, "centos6.x86_64.rpm");
        checkOsTag("Ubuntu 14", OsArchs.X_86_64, "14.04", true, "ubuntu12.04_amd64.deb");
        checkOsTag("Ubuntu 14", OsArchs.X_86_64, "14.04", false, "x86_64.deb");
        checkOsTag("Debian 7up", OsArchs.I386, "7ish", true, "debian7_x86.deb");
        Assert.assertEquals(new CouchbaseNodeSshDriver.DownloadLinkSegmentComputer(null, true, "test").getOsTag(), "centos6.x86_64.rpm");
        Assert.assertEquals(new CouchbaseNodeSshDriver.DownloadLinkSegmentComputer(null, false, "test").getOsTag(), "x86_64.rpm");
    }

    protected void checkOsTag(String os, String arch, String version, boolean isV30, String expectedTag) {
        Assert.assertEquals(new CouchbaseNodeSshDriver.DownloadLinkSegmentComputer(new BasicOsDetails(os, arch, version), isV30, "test").getOsTag(), expectedTag);
    }
    
}
