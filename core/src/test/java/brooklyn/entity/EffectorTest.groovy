package brooklyn.entity

import static org.junit.Assert.*

import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractEffector
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.basic.DefaultValue
import brooklyn.entity.basic.Description
import brooklyn.entity.basic.EffectorInferredFromAnnotatedMethod
import brooklyn.entity.basic.EffectorWithExplicitImplementation;
import brooklyn.entity.basic.NamedParameter
import brooklyn.management.ManagementContext
import brooklyn.management.internal.LocalManagementContext


class EffectorTest {
    private static final Logger log = LoggerFactory.getLogger(EffectorTest.class);
    // FIXME remove this when we have a process for setting logging...
//    static {
//        log.metaClass {
//            warn = { String a -> println "WARN "+a }
//            warn = { String a, Throwable t -> println "WARN "+a; t.printStackTrace(); }
//            info = { String a -> println "INFO "+a }
//        }
//    }
    
    public static interface CanSayHi {
        static Effector<String> SAY_HI_1 = new EffectorWithExplicitImplementation<CanSayHi,String>("sayHi1", String.class, [
                    [ "name", String.class, "person to say hi to" ] as BasicParameterType<String>,
                    [ "greeting", String.class, "what to say as greeting", "hello" ] as BasicParameterType<String>
                ], "says hello to a person") {
            public String invokeEffector(CanSayHi e, Map m) {
                e.sayHi1(m)
            }
        };

        static Effector<String> SAY_HI_2 = new EffectorInferredFromAnnotatedMethod<String>(CanSayHi.class, "sayHi2", "says hello");

        public String sayHi1(String name, String greeting);
        public String sayHi2(
            @NamedParameter("name") String name,
            @NamedParameter("greeting") @DefaultValue("hello") @Description("what to say") String greeting);
    }
        
    public static class MyEntity extends AbstractEntity implements CanSayHi {
        public String sayHi1(String name, String greeting) { "$greeting $name" }
        public String sayHi2(String name, String greeting) { "$greeting $name" }

        ManagementContext mgmt = new LocalManagementContext()
        
        //for testing
        @Override
        public ManagementContext getManagementContext() {
            if (!getApplication()) return mgmt;
            return super.getManagementContext();
        }
        
    }

    @Test
    public void testFindEffectors() {
        MyEntity e = new MyEntity();
        
        assertEquals("sayHi1", e.SAY_HI_1.getName());    
        assertEquals(["name", "greeting"], e.SAY_HI_1.getParameters()[0..1]*.getName());    

        assertEquals("sayHi2", e.SAY_HI_2.getName());
        assertEquals(["name", "greeting"], e.SAY_HI_2.getParameters()[0..1]*.getName());
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

    //TODO test edge/error conditions
    //(missing parameters, wrong number of params, etc)    
    
}

