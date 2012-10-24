package brooklyn.entity

import static org.testng.Assert.*

import org.testng.annotations.Test

import groovy.transform.InheritConstructors;

import java.beans.ReflectionUtils;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractEffector
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.basic.DefaultValue
import brooklyn.entity.basic.Description
import brooklyn.entity.basic.ExplicitEffector
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.basic.NamedParameter
import brooklyn.entity.trait.Startable;
import brooklyn.management.ManagementContext
import brooklyn.management.internal.LocalManagementContext
import brooklyn.management.Task
import brooklyn.management.ExecutionContext

/**
 * Test the operation of the {@link Effector} implementations.
 *
 * TODO clarify test purpose
 */
public class EffectorSayHiTest {
    private static final Logger log = LoggerFactory.getLogger(EffectorSayHiTest.class);

    @Test
    public void testFindEffectors() {
        MyEntity e = new MyEntity();
        new LocalManagementContext().manage(e);

        assertEquals("sayHi1", e.SAY_HI_1.getName());
        assertEquals(["name", "greeting"], e.SAY_HI_1.getParameters()[0..1]*.getName());
        assertEquals("says hello", e.SAY_HI_1.getDescription());
		
		assertEquals("sayHi1", e.SAY_HI_1_ALT.getName());
		assertEquals(["name", "greeting"], e.SAY_HI_1_ALT.getParameters()[0..1]*.getName());
		assertEquals("says hello", e.SAY_HI_1_ALT.getDescription());
		
		assertEquals("sayHi2", e.SAY_HI_2.getName());
		assertEquals(["name", "greeting"], e.SAY_HI_2.getParameters()[0..1]*.getName());
		assertEquals("says hello", e.SAY_HI_2.getDescription());
    }

    // XXX parameter type annotations do NOT work on external Java interface effector definitions
    //     that use EffectorInferredFromAnnotatedMethod.
	// Alex% please provide more information or failing test case. they should work AFAICT. the fact that
	//     the interface method gets implemented doesn't affect how the effector is defined, it is
	//     built up with explicit reference to the interface class (as CanSayHi is an interface above)
    @Test
    public void testFindTraitEffectors() {
        assertEquals("locations", Startable.START.getParameters()[0].getName());
    }

    @Test
    public void testInvokeEffectorMethod1BypassInterception() {
        MyEntity e = new MyEntity();
        new LocalManagementContext().manage(e);

        String name = "sayHi1"
        def args = ["Bob", "hello"] as Object[]

        //try the alt syntax recommended from web
        def metaMethod = e.metaClass.getMetaMethod(name, args)
        if (metaMethod==null)
            throw new IllegalArgumentException("Invalid arguments (no method found) for method $name: "+args);
        assertEquals("hello Bob", metaMethod.invoke(e, args))
    }

    @Test
    public void testInvokeEffectorMethod2BypassInterception() {
        MyEntity e = new MyEntity();
        new LocalManagementContext().manage(e);

        String name = "sayHi2"
        def args = ["Bob", "hello"] as Object[]
        assertEquals("hello Bob", e.metaClass.invokeMethod(e, name, args))
    }

    @Test
    public void testInvokeEffectors1() {
        MyEntity e = new MyEntity();
        new LocalManagementContext().manage(e);

        assertEquals("hi Bob", e.sayHi1("Bob", "hi"))
        assertEquals("hello Bob", e.sayHi1("Bob"))

        assertEquals("hi Bob", e.sayHi1(name: "Bob", greeting:"hi"))
        assertEquals("hello Bob", e.sayHi1(name: "Bob"))

        assertEquals("hello Bob", e.SAY_HI_1.call(e, [name:"Bob"]) )
        assertEquals("hello Bob", e.invoke(e.SAY_HI_1, [name:"Bob"]).get() );
		
		assertEquals("hello Bob", e.SAY_HI_1_ALT.call(e, [name:"Bob"]) )
    }

    @Test
    public void testInvokeEffectors2() {
        MyEntity e = new MyEntity();
        new LocalManagementContext().manage(e);
        
        assertEquals("hi Bob", e.sayHi2("Bob", "hi"))
        assertEquals("hello Bob", e.sayHi2("Bob"))

        assertEquals("hi Bob", e.sayHi2(name: "Bob", greeting:"hi"))
        assertEquals("hello Bob", e.sayHi2(name: "Bob"))

        assertEquals("hello Bob", e.SAY_HI_2.call(e, [name:"Bob"]) )
        assertEquals("hello Bob", e.invoke(e.SAY_HI_2, [name:"Bob"]).get() );
    }

    @Test
    public void testCanRetrieveTaskForEffector() {
        MyEntity e = new MyEntity();
        new LocalManagementContext().manage(e);
        
        e.sayHi2("Bob", "hi")

        ManagementContext managementContext = e.getManagementContext()

        Set<Task> tasks = managementContext.getExecutionManager().getTasksWithAllTags([e,"EFFECTOR"])
        assertEquals(tasks.size(), 1)
        assertTrue(tasks.iterator().next().getDescription().contains("sayHi2"))
    }

    @Test
    public void testCanExcludeNonEffectorTasks() {
        MyEntity e = new MyEntity()
        new LocalManagementContext().manage(e);
        ManagementContext managementContext = e.getManagementContext()
        ExecutionContext executionContext = managementContext.getExecutionContext()
        executionContext.submit( {} as Runnable)

        Set<Task> effectTasks = managementContext.getExecutionManager().getTasksWithAllTags([e,"EFFECTOR"])
        assertEquals(effectTasks.size(), 0)
    }

    //TODO test edge/error conditions
    //(missing parameters, wrong number of params, etc)
}

interface CanSayHi {
	//prefer following simple groovy syntax
	static Effector<String> SAY_HI_1 = new MethodEffector<String>(CanSayHi.&sayHi1);
	//slightly longer-winded pojo also supported
	static Effector<String> SAY_HI_1_ALT = new MethodEffector<String>(CanSayHi.class, "sayHi1");
	
	@Description("says hello")
	public String sayHi1(
		@NamedParameter("name") String name,
		@NamedParameter("greeting") @DefaultValue("hello") @Description("what to say") String greeting);

	//finally there is a way to provide a class/closure if needed or preferred for some odd reason
	static Effector<String> SAY_HI_2 =
	
		//groovy 1.8.2 balks at runtime during getCallSiteArray (bug 5122) if we use anonymous inner class 
//	  new ExplicitEffector<CanSayHi,String>(
//			"sayHi2", String.class, [
//					[ "name", String.class, "person to say hi to" ] as BasicParameterType<String>,
//					[ "greeting", String.class, "what to say as greeting", "hello" ] as BasicParameterType<String>
//				],
//			"says hello to a person") {
//		public String invokeEffector(CanSayHi e, Map m) {
//			e.sayHi2(m)
//		}
//	};
	//following is a workaround, not greatly enamoured of it... but MethodEffector is generally preferred anyway
		ExplicitEffector.create("sayHi2", String.class, [
					[ "name", String.class, "person to say hi to" ] as BasicParameterType<String>,
					[ "greeting", String.class, "what to say as greeting", "hello" ] as BasicParameterType<String>
				],
			"says hello", { e, m -> e.sayHi2(m) })
	
	public String sayHi2(String name, String greeting);

}

@InheritConstructors
public class MyEntity extends SimpleEntity implements CanSayHi {
	public String sayHi1(String name, String greeting) { "$greeting $name" }
	public String sayHi2(String name, String greeting) { "$greeting $name" }
}

