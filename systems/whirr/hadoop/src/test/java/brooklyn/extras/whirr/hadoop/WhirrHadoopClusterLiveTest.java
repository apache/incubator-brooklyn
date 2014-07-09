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
package brooklyn.extras.whirr.hadoop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;

public class WhirrHadoopClusterLiveTest {

    private static final Logger log = LoggerFactory.getLogger(WhirrHadoopClusterLiveTest.class);
    
    public static final String PROVIDER = "aws-ec2";
    public static final String LOCATION_SPEC = "jclouds:"+PROVIDER;
    
    protected TestApplication app;
    protected Location jcloudsLocation;
    private BrooklynProperties brooklynProperties;
    private LocalManagementContext ctx;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        ctx = new LocalManagementContext(brooklynProperties);
        app = ApplicationBuilder.newManagedApp(TestApplication.class, ctx);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAllCatching(app.getManagementContext());
    }

    @Test(groups = { "Live" })
    public void testAwsRollout() {
        try {
            //final WhirrHadoopCluster hadoop = 
            app.createAndManageChild(EntitySpec.create(WhirrHadoopCluster.class));
            Location loc = ctx.getLocationRegistry().resolve(LOCATION_SPEC);
            app.start(ImmutableList.of(loc));
        } finally {
            log.info("completed AWS Hadoop test: "+app.getAllAttributes());
            Entities.dumpInfo(app);
        }
    }

}
