package brooklyn.entity

import brooklyn.management.internal.LocalManagementContext

import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.annotation.EffectorParam
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.ExplicitEffector
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.proxying.EntitySpec
import brooklyn.entity.proxying.ImplementedBy
import brooklyn.entity.trait.Startable
import brooklyn.management.ManagementContext
import brooklyn.management.Task
import brooklyn.management.internal.EffectorUtils
import brooklyn.test.entity.TestApplication

/**
 * Test the operation of the {@link Effector} implementations.
 *
 * TODO clarify test purpose
 */
public class EffectorSayHiGroovyTest {
    private static final Logger log = LoggerFactory.getLogger(EffectorSayHiTest.class);

    private TestApplication app;
    private MyEntity e;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        e = app.createAndManageChild(EntitySpec.create(MyEntity.class));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        LocalManagementContext.terminateAll();
    }

    @Test
    public void testFindEffectors() {
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

    @Test
    public void testFindTraitEffectors() {
        assertEquals("locations", Startable.START.getParameters()[0].getName());
    }

    @Test
    public void testInvokeEffectorMethod1BypassInterception() {
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
        String name = "sayHi2"
        def args = ["Bob", "hello"] as Object[]
        assertEquals("hello Bob", e.metaClass.invokeMethod(e, name, args))
    }

    @Test
    public void testInvokeEffectors1() {
        assertEquals("hi Bob", e.sayHi1("Bob", "hi"))

        assertEquals("hello Bob", e.SAY_HI_1.call(e, [name:"Bob"]) )
        assertEquals("hello Bob", e.invoke(e.SAY_HI_1, [name:"Bob"]).get() );

		assertEquals("hello Bob", e.SAY_HI_1_ALT.call(e, [name:"Bob"]) )
    }

    @Test
    public void testInvokeEffectors2() {
        assertEquals("hi Bob", e.sayHi2("Bob", "hi"))

        assertEquals("hello Bob", e.SAY_HI_2.call(e, [name:"Bob"]) )
        assertEquals("hello Bob", e.invoke(e.SAY_HI_2, [name:"Bob"]).get() );
        
    }

    @Test
    public void testCanRetrieveTaskForEffector() {
        e.sayHi2("Bob", "hi")

        ManagementContext managementContext = e.getManagementContext()

        Set<Task> tasks = managementContext.getExecutionManager().getTasksWithAllTags([e,"EFFECTOR"])
        assertEquals(tasks.size(), 1)
        assertTrue(tasks.iterator().next().getDescription().contains("sayHi2"))
    }
}
public interface CanSayHi {
	//prefer following simple groovy syntax
	static Effector<String> SAY_HI_1 = new MethodEffector<String>(CanSayHi.&sayHi1);
	//slightly longer-winded pojo also supported
	static Effector<String> SAY_HI_1_ALT = new MethodEffector<String>(CanSayHi.class, "sayHi1");

	@brooklyn.entity.annotation.Effector(description="says hello")
	public String sayHi1(
		@EffectorParam(name="name") String name,
		@EffectorParam(name="greeting", defaultValue="hello", description="what to say") String greeting);

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
					new BasicParameterType<String>("name", String.class, "person to say hi to"),
					new BasicParameterType<String>("greeting", String.class, "what to say as greeting", "hello")
				],
			"says hello", { e, m ->
                def args = EffectorUtils.prepareArgsForEffector(SAY_HI_2, m);
                e.sayHi2(args[0], args[1]) })

	public String sayHi2(String name, String greeting);

}

@ImplementedBy(MyEntityImpl.class)
public interface MyEntity extends Entity, CanSayHi {
}

public class MyEntityImpl extends AbstractEntity implements MyEntity {
    public String sayHi1(String name, String greeting) { "$greeting $name" }
	public String sayHi2(String name, String greeting) { "$greeting $name" }
}
