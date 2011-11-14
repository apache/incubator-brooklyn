package brooklyn.entity

import static org.testng.Assert.*
import groovy.transform.InheritConstructors;

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.basic.DefaultValue
import brooklyn.entity.basic.Description
import brooklyn.entity.basic.EffectorInferredFromAnnotatedMethod
import brooklyn.entity.basic.EffectorWithExplicitImplementation
import brooklyn.entity.basic.NamedParameter
import brooklyn.entity.trait.Startable
import brooklyn.management.ExecutionContext
import brooklyn.management.ManagementContext
import brooklyn.management.Task

/**
 * Test the operation of the {@link Effector} implementations.
 *
 * TODO clarify test purpose
 */
public class EffectorSayHiTest {
    private static final Logger log = LoggerFactory.getLogger(EffectorSayHiTest.class);
    // FIXME remove this when we have a process for setting logging...
//    static {
//        log.metaClass {
//            warn = { String a -> println "WARN "+a }
//            warn = { String a, Throwable t -> println "WARN "+a; t.printStackTrace(); }
//            info = { String a -> println "INFO "+a }
//        }
//    }

    @Test
    public void testFindEffectors() {
        MyEntity e = new MyEntity();

        assertEquals("sayHi1", e.SAY_HI_1.getName());
        assertEquals(["name", "greeting"], e.SAY_HI_1.getParameters()[0..1]*.getName());

        assertEquals("sayHi2", e.SAY_HI_2.getName());
        assertEquals(["name", "greeting"], e.SAY_HI_2.getParameters()[0..1]*.getName());
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

        String name = "sayHi1"
        def args = ["Bob", "hello"] as Object[]
        assertEquals("hello Bob", e.metaClass.invokeMethod(e, name, args))
    }

    @Test
    public void testInvokeEffectorMethod2BypassInterception() {
        MyEntity e = new MyEntity();

        String name = "sayHi2"
        def args = ["Bob", "hello"] as Object[]

        //try the alt syntax recommended from web
        def metaMethod = e.metaClass.getMetaMethod(name, args)
        if (metaMethod==null)
            throw new IllegalArgumentException("Invalid arguments (no method found) for method $name: "+args);
        assertEquals("hello Bob", metaMethod.invoke(e, args))
    }

    @Test
    public void testInvokeEffectors1() {
        MyEntity e = new MyEntity();

        assertEquals("hi Bob", e.sayHi1("Bob", "hi"))
        assertEquals("hello Bob", e.sayHi1("Bob"))

        assertEquals("hi Bob", e.sayHi1(name: "Bob", greeting:"hi"))
        assertEquals("hello Bob", e.sayHi1(name: "Bob"))

        assertEquals("hello Bob", e.SAY_HI_1.call(e, [name:"Bob"]) )
        assertEquals("hello Bob", e.invoke(e.SAY_HI_1, [name:"Bob"]).get() );
    }

    @Test
    public void testInvokeEffectors2() {
        MyEntity e = new MyEntity();

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
        e.sayHi1("Bob", "hi")

        ManagementContext managementContext = e.getManagementContext()

        Set<Task> tasks = managementContext.getExecutionManager().getTasksWithAllTags([e,"EFFECTOR"])
        assertEquals(tasks.size(), 1)
        assertTrue(tasks.iterator().next().getDescription().contains("sayHi1"))
    }

    @Test
    public void testCanExcludeNonEffectorTasks() {
        MyEntity e = new MyEntity()
        ManagementContext managementContext = e.getManagementContext()
        ExecutionContext executionContext = managementContext.getExecutionContext()
        executionContext.submit( {} as Runnable)

        Set<Task> effectTasks = managementContext.getExecutionManager().getTasksWithAllTags([e,"EFFECTOR"])
        assertEquals(effectTasks.size(), 0)
    }

    //TODO test edge/error conditions
    //(missing parameters, wrong number of params, etc)
}

private class SayHi1 extends EffectorWithExplicitImplementation<CanSayHi,String> {

	public SayHi1() {
		super("sayHi1", String.class, [
					[ "name", String.class, "person to say hi to" ] as BasicParameterType<String>,
					[ "greeting", String.class, "what to say as greeting", "hello" ] as BasicParameterType<String>
				],
			"says hello to a person");
	}	
	public String invokeEffector(CanSayHi e, Map m) {
		e.sayHi1(m)
	}
}

interface CanSayHi {
	static Effector<String> SAY_HI_1 =
		//FIXME how naff... groovy 1.8.2 balks at runtime during getCallSiteArray is this is an anonymous inner class 
		new SayHi1();
//	  new EffectorWithExplicitImplementation<CanSayHi,String>(
//			"sayHi1", String.class, [
//					[ "name", String.class, "person to say hi to" ] as BasicParameterType<String>,
//					[ "greeting", String.class, "what to say as greeting", "hello" ] as BasicParameterType<String>
//				],
//			"says hello to a person") {
//		public String invokeEffector(CanSayHi e, Map m) {
//			e.sayHi1(m)
//		}
//	};
	public String sayHi1(String name, String greeting);

	static Effector<String> SAY_HI_2 = new EffectorInferredFromAnnotatedMethod<String>(CanSayHi.class, "sayHi2", "says hello");
	public String sayHi2(
		@NamedParameter("name") String name,
		@NamedParameter("greeting") @DefaultValue("hello") @Description("what to say") String greeting);
}

public class MyEntity extends LocallyManagedEntity implements CanSayHi {
	public String sayHi1(String name, String greeting) { "$greeting $name" }
	public String sayHi2(String name, String greeting) { "$greeting $name" }
}

