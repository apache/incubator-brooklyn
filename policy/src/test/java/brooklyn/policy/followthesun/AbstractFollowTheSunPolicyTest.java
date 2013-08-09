package brooklyn.policy.followthesun;

import static org.testng.Assert.assertEquals;

import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.policy.loadbalancing.MockContainerEntity;
import brooklyn.policy.loadbalancing.MockItemEntity;
import brooklyn.policy.loadbalancing.MockItemEntityImpl;
import brooklyn.policy.loadbalancing.Movable;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class AbstractFollowTheSunPolicyTest {
    
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractFollowTheSunPolicyTest.class);
    
    protected static final long TIMEOUT_MS = 10*1000;
    protected static final long SHORT_WAIT_MS = 250;
    
    protected static final long CONTAINER_STARTUP_DELAY_MS = 100;
    
    public static final AttributeSensor<Map<Entity, Double>> TEST_METRIC =
        new BasicAttributeSensor(Map.class, "test.metric", "Dummy workrate for test entities");

    protected TestApplication app;
    protected SimulatedLocation loc1;
    protected SimulatedLocation loc2;
    protected FollowTheSunPool pool;
    protected DefaultFollowTheSunModel<Entity, Movable> model;
    protected FollowTheSunPolicy policy;
    protected Group containerGroup;
    protected Group itemGroup;
    protected Random random = new Random();
    
    @BeforeMethod(alwaysRun=true)
    public void before() throws Exception {
        LOG.debug("In AbstractFollowTheSunPolicyTest.before()");

        MockItemEntityImpl.totalMoveCount.set(0);
        
        loc1 = new SimulatedLocation(MutableMap.of("name", "loc1"));
        loc2 = new SimulatedLocation(MutableMap.of("name", "loc2"));
        
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        containerGroup = app.createAndManageChild(EntitySpec.create(DynamicGroup.class)
                .displayName("containerGroup")
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(MockContainerEntity.class)));
        
        itemGroup = app.createAndManageChild(EntitySpec.create(DynamicGroup.class)
                .displayName("itemGroup")
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(MockItemEntity.class)));
        model = new DefaultFollowTheSunModel<Entity, Movable>("pool-model");
        pool = app.createAndManageChild(EntitySpec.create(FollowTheSunPool.class));
        pool.setContents(containerGroup, itemGroup);
        policy = new FollowTheSunPolicy(TEST_METRIC, model, FollowTheSunParameters.newDefault());
        pool.addPolicy(policy);
        app.start(ImmutableList.of(loc1, loc2));
    }
    
    @AfterMethod(alwaysRun=true)
    public void after() {
        if (pool != null && policy != null) pool.removePolicy(policy);
        if (app != null) Entities.destroyAll(app.getManagementContext());
        MockItemEntityImpl.totalMoveCount.set(0);
    }
    
    /**
     * Asserts that the given container have the given expected workrates (by querying the containers directly).
     * Accepts an accuracy of "precision" for each container's workrate.
     */
    protected void assertItemDistributionEventually(final Map<MockContainerEntity, ? extends Collection<MockItemEntity>> expected) {
        try {
            Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
                public void run() {
                    assertItemDistribution(expected);
                }});
        } catch (AssertionError e) {
            String errMsg = e.getMessage()+"; "+verboseDumpToString();
            throw new RuntimeException(errMsg, e);
        }
    }

    protected void assertItemDistributionContinually(final Map<MockContainerEntity, Collection<MockItemEntity>> expected) {
        try {
            Asserts.succeedsContinually(MutableMap.of("timeout", SHORT_WAIT_MS), new Runnable() {
                public void run() {
                    assertItemDistribution(expected);
                }});
        } catch (AssertionError e) {
            String errMsg = e.getMessage()+"; "+verboseDumpToString();
            throw new RuntimeException(errMsg, e);
        }
    }

    protected void assertItemDistribution(Map<MockContainerEntity, ? extends Collection<MockItemEntity>> expected) {
        String errMsg = verboseDumpToString();
        for (Map.Entry<MockContainerEntity, ? extends Collection<MockItemEntity>> entry : expected.entrySet()) {
            MockContainerEntity container = entry.getKey();
            Collection<MockItemEntity> expectedItems = entry.getValue();
            
            assertEquals(ImmutableSet.copyOf(container.getBalanceableItems()), ImmutableSet.copyOf(expectedItems));
        }
    }

    protected String verboseDumpToString() {
        Iterable<MockContainerEntity> containers = Iterables.filter(app.getManagementContext().getEntities(), MockContainerEntity.class);
        //Collection<MockContainerEntity> containers = app.getManagementContext().getEntities().findAll { it instanceof MockContainerEntity }
        Iterable<Set<Movable>> itemDistribution = Iterables.transform(containers, new Function<MockContainerEntity, Set<Movable>>() {
            public Set<Movable> apply(MockContainerEntity input) {
                return input.getBalanceableItems();
            }});
        String modelItemDistribution = model.itemDistributionToString();
        return "containers="+containers+"; itemDistribution="+itemDistribution+"; model="+modelItemDistribution+"; "+
                "totalMoves="+MockItemEntityImpl.totalMoveCount;
    }
    
    protected MockContainerEntity newContainer(TestApplication app, Location loc, String name) {
        return newAsyncContainer(app, loc, name, 0);
    }
    
    /**
     * Creates a new container that will take "delay" millis to complete its start-up.
     */
    protected MockContainerEntity newAsyncContainer(TestApplication app, Location loc, String name, long delay) {
        // FIXME Is this comment true?
        // Annoyingly, can't set parent until after the threshold config has been defined.
        MockContainerEntity container = app.createAndManageChild(EntitySpec.create(MockContainerEntity.class)
                .displayName(name)
                .configure(MockContainerEntity.DELAY, delay));
        LOG.debug("Managed new container {}", container);
        container.start(ImmutableList.of(loc));
        return container;
    }

    protected static MockItemEntity newLockedItem(TestApplication app, MockContainerEntity container, String name) {
        MockItemEntity item = app.createAndManageChild(EntitySpec.create(MockItemEntity.class)
                .displayName(name)
                .configure(MockItemEntity.IMMOVABLE, true));
        LOG.debug("Managed new locked item {}", container);
        if (container != null) {
            item.move(container);
        }
        return item;
    }
    
    protected static MockItemEntity newItem(TestApplication app, MockContainerEntity container, String name) {
        MockItemEntity item = app.createAndManageChild(EntitySpec.create(MockItemEntity.class)
                .displayName(name));
        LOG.debug("Managed new item {}", container);
        if (container != null) {
            item.move(container);
        }
        return item;
    }
    
    protected static MockItemEntity newItem(TestApplication app, MockContainerEntity container, String name, Map<? extends Entity, Double> workpattern) {
        MockItemEntity item = newItem(app, container, name);
        if (workpattern != null) {
            ((EntityLocal)item).setAttribute(TEST_METRIC, (Map) workpattern);
        }
        return item;
    }
}
