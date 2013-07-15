package brooklyn.entity;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.NamedParameter;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableMap;
import brooklyn.util.task.BasicTask;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Test the operation of the {@link Effector} implementations.
 *
 * TODO clarify test purpose
 */
public class EffectorSayHiTest {
    
    //TODO test edge/error conditions
    //(missing parameters, wrong number of params, etc)

    private static final Logger log = LoggerFactory.getLogger(EffectorSayHiTest.class);

    private TestApplication app;
    private MyEntity e;
    private ManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        e = app.createAndManageChild(EntitySpecs.spec(MyEntity.class));
        managementContext = ((EntityInternal)e).getManagementContext();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testFindEffectorMetaData() {
        assertEquals("sayHi1", e.SAY_HI_1.getName());
        assertEquals("says hello", e.SAY_HI_1.getDescription());
        
        assertEquals(ImmutableList.of("name", "greeting"), getParameterNames(e.SAY_HI_1));
        assertEquals(MutableMap.of("name", null, "greeting", "what to say"), getParameterDescriptions(e.SAY_HI_1));
    }

    @Test
    public void testFindTraitEffectors() {
        assertEquals(ImmutableList.of("locations"), getParameterNames(Startable.START));
    }

    @Test
    public void testInvokeEffectors1() throws Exception {
        assertEquals("hi Bob", e.sayHi1("Bob", "hi"));

        assertEquals("hi Bob", e.SAY_HI_1.call(e, ImmutableMap.of("name", "Bob", "greeting", "hi")) );
        assertEquals("hi Bob", e.invoke(e.SAY_HI_1, ImmutableMap.of("name", "Bob", "greeting", "hi")).get() );
        
        // and with default greeting param value
        assertEquals("hi Bob", e.SAY_HI_1.call(e, ImmutableMap.of("name", "Bob", "greeting", "hi")) );
        assertEquals("hello Bob", e.invoke(e.SAY_HI_1, ImmutableMap.of("name", "Bob")).get() );
    }

    @Test
    public void testCanRetrieveTaskForEffector() {
        e.sayHi1("Bob", "hi");

        Set<Task<?>> tasks = managementContext.getExecutionManager().getTasksWithAllTags(ImmutableList.of(e,ManagementContextInternal.EFFECTOR_TAG));
        assertEquals(tasks.size(), 1);
        assertTrue(tasks.iterator().next().getDescription().contains("sayHi1"));
    }

    @Test
    public void testDelegatedNestedEffectorNotRepresentedAsTask() {
        e.delegateSayHi1("Bob", "hi");

        Set<Task<?>> tasks = managementContext.getExecutionManager().getTasksWithAllTags(ImmutableList.of(e,ManagementContextInternal.EFFECTOR_TAG));
        assertEquals(tasks.size(), 1);
        assertTrue(tasks.iterator().next().getDescription().contains("delegateSayHi1"));
        assertFalse(tasks.iterator().next().getDescription().contains("sayHi1"));
    }

    @Test
    public void testCanExcludeNonEffectorTasks() throws Exception {
        ExecutionContext executionContext = managementContext.getExecutionContext(e);
        executionContext.submit(new BasicTask<Void>(new Runnable() { public void run() {} }));

        Set<Task<?>> effectTasks = managementContext.getExecutionManager().getTasksWithAllTags(ImmutableList.of(e,ManagementContextInternal.EFFECTOR_TAG));
        assertEquals(effectTasks.size(), 0);
    }

    public interface CanSayHi {
    	static MethodEffector<String> SAY_HI_1 = new MethodEffector<String>(CanSayHi.class, "sayHi1");
        static MethodEffector<String> DELEGATE_SAY_HI_1 = new MethodEffector<String>(CanSayHi.class, "delegateSayHi1");
    
    	@brooklyn.entity.annotation.Effector(description="says hello")
    	public String sayHi1(
    		@EffectorParam(name="name") String name,
    		@EffectorParam(name="greeting", defaultValue="hello", description="what to say") String greeting);
    	
    	@brooklyn.entity.annotation.Effector(description="delegate says hello")
        public String delegateSayHi1(
            @EffectorParam(name="name") String name,
            @EffectorParam(name="greeting") String greeting);
    }

    @ImplementedBy(MyEntityImpl.class)
    public interface MyEntity extends Entity, CanSayHi {
    }
    
    public static class MyEntityImpl extends AbstractEntity implements MyEntity {
        @Override
        public String sayHi1(String name, String greeting) {
            return greeting+" "+name;
        }
        @Override
        public String delegateSayHi1(String name, String greeting) {
            return sayHi1(name, greeting);
        }
    }
    
    private List<String> getParameterNames(Effector<?> effector) {
        return ImmutableList.copyOf(getParameterDescriptions(effector).keySet());
    }
    
    private Map<String, String> getParameterDescriptions(Effector<?> effector) {
        Map<String,String> result = Maps.newLinkedHashMap();
        for (ParameterType<?> parameter : effector.getParameters()) {
            result.put(parameter.getName(), parameter.getDescription());
        }
        return result;
    }
}
