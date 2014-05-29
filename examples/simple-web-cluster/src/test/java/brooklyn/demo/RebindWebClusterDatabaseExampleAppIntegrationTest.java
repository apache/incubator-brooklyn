package brooklyn.demo;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.enricher.HttpLatencyDetector;
import brooklyn.enricher.basic.Propagator;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.java.JavaEntityMethods;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestFixture;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.Location;
import brooklyn.management.SubscriptionHandle;
import brooklyn.policy.Enricher;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.HttpTestUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Duration;

import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;


public class RebindWebClusterDatabaseExampleAppIntegrationTest extends RebindTestFixture<StartableApplication> {

    // FIXME Fails because:
    //  - ControlledDynamicWebAppCluster should do rescanEntities to get all the members of its child.
    //    But it doesn't on rebind; when should it do this?

    private Location origLoc;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        origLoc = origManagementContext.getLocationRegistry().resolve("localhost");
    }
    
    @Override
    protected StartableApplication createApp() {
        return origManagementContext.getEntityManager().createEntity(EntitySpec.create(StartableApplication.class)
                .impl(WebClusterDatabaseExampleApp.class)
                .configure(DynamicCluster.INITIAL_SIZE, 2));
    }
    
    @Override
    protected StartableApplication rebind(boolean checkSerializable) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        if (checkSerializable) {
            RebindTestUtils.checkCurrentMementoSerializable(origApp);
        }
        
        // Must also terminate the original management context, otherwise it will keep trying to write 
        // mementos due to the frequent attribute-changes
        origManagementContext.terminate();
        
        return (StartableApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }


    @Test(groups="Integration")
    public void testRestoresSimpleApp() throws Exception {
        origApp.start(ImmutableList.of(origLoc));
        
        assertAppFunctional(origApp);
        
        newApp = rebind(false);
        assertAppFunctional(newApp);
    }
    
    private void assertAppFunctional(StartableApplication app) throws Exception {
        // expect standard config to (still) be set
        assertNotNull(app.getConfig(WebClusterDatabaseExampleApp.WAR_PATH));
        assertEquals(app.getConfig(WebClusterDatabaseExampleApp.USE_HTTPS), Boolean.FALSE);
        assertNotNull(app.getConfig(WebClusterDatabaseExampleApp.DB_SETUP_SQL_URL));

        // expect entities to be there
        MySqlNode mysql = (MySqlNode) Iterables.find(app.getChildren(), Predicates.instanceOf(MySqlNode.class));
        ControlledDynamicWebAppCluster web = (ControlledDynamicWebAppCluster) Iterables.find(app.getChildren(), Predicates.instanceOf(ControlledDynamicWebAppCluster.class));
        final NginxController nginx = (NginxController) Iterables.find(web.getChildren(), Predicates.instanceOf(NginxController.class));
        DynamicWebAppCluster webCluster = (DynamicWebAppCluster) Iterables.find(web.getChildren(), Predicates.instanceOf(DynamicWebAppCluster.class));
        Collection<Entity> appservers = web.getMembers();
        assertEquals(appservers.size(), 2);
        String clusterUrl = checkNotNull(app.getAttribute(WebClusterDatabaseExampleApp.ROOT_URL), "cluster url");
        String dbUrl = checkNotNull(mysql.getAttribute(MySqlNode.DATASTORE_URL), "database url");
        final String expectedJdbcUrl = String.format("jdbc:%s%s?user=%s\\&password=%s", dbUrl, WebClusterDatabaseExampleApp.DB_TABLE, 
                WebClusterDatabaseExampleApp.DB_USERNAME, WebClusterDatabaseExampleApp.DB_PASSWORD);

        // FIXME the rebind policy is reconfiguring nginx temporarily; the members are being removed!
        Thread.sleep(10000);
        
        // expect web-app to be reachable, and wired up to database
        HttpTestUtils.assertHttpStatusCodeEquals(clusterUrl, 200);
        for (Entity appserver : appservers) {
            String appserverUrl = checkNotNull(appserver.getAttribute(JBoss7Server.ROOT_URL), "appserver url of "+appserver);

            HttpTestUtils.assertHttpStatusCodeEquals(appserverUrl, 200);
            assertEquals(expectedJdbcUrl, appserver.getConfig(JavaEntityMethods.javaSysProp("brooklyn.example.db.url")), "of "+appserver);
        }

        // expect auto-scaler policy to be there, and to be functional (e.g. can trigger resize)
        AutoScalerPolicy autoScalerPolicy = (AutoScalerPolicy) Iterables.find(webCluster.getPolicies(), Predicates.instanceOf(AutoScalerPolicy.class));
        
        autoScalerPolicy.setConfig(AutoScalerPolicy.MIN_POOL_SIZE, 3);
        EntityTestUtils.assertGroupSizeEqualsEventually(web, 3);
        final Collection<Entity> webMembersAfterGrow = web.getMembers();
        
        for (final Entity appserver : webMembersAfterGrow) {
            Asserts.succeedsEventually(MutableMap.of("timeout", Duration.TWO_MINUTES), new Runnable() {
                @Override public void run() {
                    String appserverUrl = checkNotNull(appserver.getAttribute(JBoss7Server.ROOT_URL), "appserver url of "+appserver);
                    HttpTestUtils.assertHttpStatusCodeEquals(appserverUrl, 200);
                    assertEquals(expectedJdbcUrl, appserver.getConfig(JavaEntityMethods.javaSysProp("brooklyn.example.db.url")), "of "+appserver);
                    Asserts.assertEqualsIgnoringOrder(nginx.getAttribute(NginxController.SERVER_POOL_TARGETS).keySet(), webMembersAfterGrow);
                }});
        }

        // expect enrichers to be there
        Iterables.find(web.getEnrichers(), Predicates.instanceOf(HttpLatencyDetector.class));
        Iterable<Enricher> propagatorEnrichers = Iterables.filter(web.getEnrichers(), Predicates.instanceOf(Propagator.class));
        assertEquals(Iterables.size(propagatorEnrichers), 2, "propagatorEnrichers="+propagatorEnrichers);

        // check we see evidence of the enrichers having an effect, and first triggering some activity
        for (int i = 0; i < 10; i++) {
            HttpTestUtils.assertHttpStatusCodeEquals(clusterUrl, 200);
        }
        EntityTestUtils.assertAttributeEqualsEventually(app, WebClusterDatabaseExampleApp.APPSERVERS_COUNT, 3);
        EntityTestUtils.assertAttributeChangesEventually(web, DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW);
        EntityTestUtils.assertAttributeChangesEventually(app, DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW);
        EntityTestUtils.assertAttributeChangesEventually(web, HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_MOST_RECENT);
        EntityTestUtils.assertAttributeChangesEventually(web, HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW);

        // Restore the web-cluster to its original size of 2
        autoScalerPolicy.setConfig(AutoScalerPolicy.MIN_POOL_SIZE, 2);
        EntityTestUtils.assertGroupSizeEqualsEventually(web, 2);
        
        final Entity removedAppserver = Iterables.getOnlyElement(Sets.difference(ImmutableSet.copyOf(webMembersAfterGrow), ImmutableSet.copyOf(web.getMembers())));
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertFalse(Entities.isManaged(removedAppserver));
            }});
    }
}
