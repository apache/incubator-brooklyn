package brooklyn.entity.webapp;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractConfigurableEntityFactory;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Changeable;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestJavaWebAppEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;

/**
 * TODO clarify test purpose
 */
public class DynamicWebAppFabricTest {
    private static final Logger log = LoggerFactory.getLogger(DynamicWebAppFabricTest.class);

    private static final long TIMEOUT_MS = 10*1000;
    
    private TestApplication app;
	private SimulatedLocation loc1;
    private SimulatedLocation loc2;
    private List<SimulatedLocation> locs;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        loc1 = new SimulatedLocation();
        loc2 = new SimulatedLocation();
        locs = ImmutableList.of(loc1, loc2);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testRequestCountAggregation() {
        // TODO Want to use EntitySpec, but TestJavaWebAppEntity not converted (so can't call spoofRequest on an interface).
        //      .configure(DynamicWebAppFabric.MEMBER_SPEC, EntitySpec.create(Entity.class).impl(TestJavaWebAppEntity.class)));
        
        DynamicWebAppFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicWebAppFabric.class)
                .configure(DynamicWebAppFabric.FACTORY, new AbstractConfigurableEntityFactory<TestJavaWebAppEntity>() {
                    @Override public TestJavaWebAppEntity newEntity2(Map flags, Entity parent) {
                        TestJavaWebAppEntity result = new TestJavaWebAppEntity(flags, parent);
                        result.setAttribute(Changeable.GROUP_SIZE, 1);
                        return result;
                    }}));
        
        app.start(locs);
        
        for (Entity member : fabric.getChildren()) {
    		((TestJavaWebAppEntity)member).spoofRequest();
        }
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), fabric, DynamicWebAppFabric.REQUEST_COUNT, 2);
        
        // Note this is time-sensitive: need to do the next two sends before the previous one has dropped out
        // of the time-window.
        for (Entity member : fabric.getChildren()) {
        	for (int i = 0; i < 2; i++) {
        		((TestJavaWebAppEntity)member).spoofRequest();
        	}
        }
    	EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), fabric, DynamicWebAppFabric.REQUEST_COUNT_PER_NODE, 3d);
    }
    
    @Test
    public void testRequestCountAggregationOverClusters() {
        DynamicWebAppFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicWebAppFabric.class)
                .configure(DynamicWebAppFabric.MEMBER_SPEC, EntitySpec.create(DynamicWebAppCluster.class)
                        .configure("initialSize", 2)
                        .configure(DynamicWebAppCluster.FACTORY, new BasicConfigurableEntityFactory<TestJavaWebAppEntity>(TestJavaWebAppEntity.class))));

        app.start(locs);
        
        for (Entity cluster : fabric.getChildren()) {
            for (Entity node : ((DynamicWebAppCluster)cluster).getMembers()) {
                ((TestJavaWebAppEntity)node).spoofRequest();
            }
        }
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), fabric, DynamicWebAppFabric.REQUEST_COUNT, 4);
        
        // Note this is time-sensitive: need to do the next two sends before the previous one has dropped out
        // of the time-window.
        for (Entity cluster : fabric.getChildren()) {
            for (Entity node : ((DynamicWebAppCluster)cluster).getMembers()) {
                for (int i = 0; i < 2; i++) {
                    ((TestJavaWebAppEntity)node).spoofRequest();
                }
            }
        }
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), fabric, DynamicWebAppFabric.REQUEST_COUNT_PER_NODE, 3d);
    }
}
