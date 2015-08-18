package com.acme.sample.brooklyn.sample.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.apache.brooklyn.api.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import org.apache.brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import org.apache.brooklyn.entity.database.mysql.MySqlNode;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.entity.webapp.JavaWebAppService;
import org.apache.brooklyn.entity.webapp.WebAppService;
import org.apache.brooklyn.api.management.ManagementContext;
import org.apache.brooklyn.core.management.internal.LocalManagementContext;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

/** 
 * Unit tests for the sample applications defined in this project.
 * Shows how to examine the spec and make assertions about configuration.
 */
@Test
public class SampleUnitTest {

    private static final Logger log = LoggerFactory.getLogger(SampleUnitTest.class);

    
    private ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        mgmt = new LocalManagementContext();
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (mgmt != null) Entities.destroyAll(mgmt);
    }

    
    public void testSampleSingleStructure() {
        StartableApplication app = mgmt.getEntityManager().createEntity(
                EntitySpec.create(StartableApplication.class, SingleWebServerSample.class));
        log.info("app from spec is: "+app);
        
        Assert.assertEquals(app.getChildren().size(), 1);
        Assert.assertNotNull( app.getChildren().iterator().next().getConfig(JavaWebAppService.ROOT_WAR) );
    }
    
    public void testSampleClusterStructure() {
        StartableApplication app = mgmt.getEntityManager().createEntity(
                EntitySpec.create(StartableApplication.class, ClusterWebServerDatabaseSample.class));
        log.info("app from spec is: "+app);
        
        Assert.assertEquals(app.getChildren().size(), 2);
        
        Entity webappCluster = Iterables.find(app.getChildren(), Predicates.instanceOf(WebAppService.class));
        Entity database = Iterables.find(app.getChildren(), Predicates.instanceOf(DatastoreCommon.class));
        
        Assert.assertNotNull( webappCluster.getConfig(JavaWebAppService.ROOT_WAR) );
        Assert.assertNotNull( database.getConfig(MySqlNode.CREATION_SCRIPT_URL) );
    }

}
