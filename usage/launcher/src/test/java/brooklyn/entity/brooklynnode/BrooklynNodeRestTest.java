package brooklyn.entity.brooklynnode;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.launcher.SimpleYamlLauncherForTests;
import brooklyn.launcher.camp.SimpleYamlLauncher;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.net.Urls;
import brooklyn.util.time.Duration;

import com.google.common.collect.Iterables;

/** REST-accessible extension of {@link BrooklynNodeTest} */
public class BrooklynNodeRestTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynNodeRestTest.class);
    
    // takes a while when run on its own, because initializing war and making some requests;
    // but there are no waits (beyond 10ms), the delay is all classloading;
    // and this tests a lot of things, REST API, Brooklyn Node, yaml deployment,
    // so feels worth it to have as a unit test
    @Test
    public void testBrooklynNodeRestDeployAndMirror() {
        SimpleYamlLauncher l = new SimpleYamlLauncherForTests();
        try {
            TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class, l.getManagementContext());

            BrooklynNode bn = app.createAndManageChild(EntitySpec.create(BrooklynNode.class, SameBrooklynNodeImpl.class));
            bn.start(MutableSet.<Location>of());
            
            URI uri = bn.getAttribute(BrooklynNode.WEB_CONSOLE_URI);
            Assert.assertNotNull(uri);
            EntityTestUtils.assertAttributeEqualsEventually(bn, Attributes.SERVICE_UP, true);
            log.info("Created BrooklynNode: "+bn);

            // deploy
            Task<?> t = bn.invoke(BrooklynNode.DEPLOY_BLUEPRINT, ConfigBag.newInstance()
                .configure(BrooklynNode.DeployBlueprintEffector.BLUEPRINT_TYPE, TestApplication.class.getName())
                .configure(BrooklynNode.DeployBlueprintEffector.BLUEPRINT_CONFIG, MutableMap.<String,Object>of("x", 1, "y", "Y"))
                .getAllConfig());
            log.info("Deployment result: "+t.getUnchecked());
            
            MutableSet<Application> apps = MutableSet.copyOf( l.getManagementContext().getApplications() );
            Assert.assertEquals(apps.size(), 2);
            apps.remove(app);
            
            Application newApp = Iterables.getOnlyElement(apps);
            Entities.dumpInfo(newApp);
            
            Assert.assertEquals(newApp.getConfig(new BasicConfigKey<Integer>(Integer.class, "x")), (Integer)1);
            
            // check mirror
            String newAppId = newApp.getId();
            BrooklynEntityMirror mirror = app.createAndManageChild(EntitySpec.create(BrooklynEntityMirror.class)
                .configure(BrooklynEntityMirror.MIRRORED_ENTITY_URL, 
                    Urls.mergePaths(uri.toString(), "/v1/applications/"+newAppId+"/entities/"+newAppId))
                .configure(BrooklynEntityMirror.MIRRORED_ENTITY_ID, newAppId)
                .configure(BrooklynEntityMirror.POLL_PERIOD, Duration.millis(10)));
            
            Entities.dumpInfo(mirror);
            
            EntityTestUtils.assertAttributeEqualsEventually(mirror, Attributes.SERVICE_UP, true);
            
            ((EntityInternal)newApp).setAttribute(TestEntity.NAME, "foo");
            EntityTestUtils.assertAttributeEqualsEventually(mirror, TestEntity.NAME, "foo");
            log.info("Mirror successfully validated");
            
        } finally {
            l.destroyAll();
        }
        log.info("DONE");
    }
}
