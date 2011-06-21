package brooklyn.entity

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import brooklyn.entity.basic.AbstractEffector
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.basic.DefaultValue;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.EffectorInferredFromAnnotatedMethod;
import brooklyn.entity.basic.NamedParameter;


class EffectorTest {

	public interface CanSayHi {
		static Effector<String> SAY_HI_1 = new AbstractEffector<CanSayHi,String>("sayHi", String.class, 
			[
				[ "name", String.class, "person to say hi to" ] as BasicParameterType<String>,
				[ "greeting", String.class, "what to say as greeting", "hello" ] as BasicParameterType<String>
			], 
			"says hello to a person") {
				public String call(CanSayHi e, Map m) { e.sayHi(m) }
		};

		static Effector<String> SAY_HI_2 = new EffectorInferredFromAnnotatedMethod<String>(CanSayHi.class, "sayHi", "says hello");

		public String sayHi( 
			@NamedParameter("name") String name,
			@NamedParameter("greeting") @DefaultValue("hello") @Description("what to say") String greeting);

//		String sayHi(String name="");
//		String sayHi();
//		String sayHi(String name);
		
	}
		
	public static class MyEntity extends AbstractEntity implements CanSayHi {
		public String sayHi(String name, String greeting) { "$greeting $name" }
	}

	@Test
	public void testFindEffectors() {
		MyEntity e = new MyEntity();
		
		assertEquals("hello Bob", e.sayHi("Bob", "hello"))

		assertEquals("sayHi", e.SAY_HI_1.getName());	
		assertEquals(["name", "greeting"], e.SAY_HI_1.getParameters()[0..1]*.getName());	

		assertEquals("sayHi", e.SAY_HI_2.getName());
		assertEquals(["name", "greeting"], e.SAY_HI_2.getParameters()[0..1]*.getName());

//		//TODO test invocation with map interception
//		assertEquals("hello Bob", e.sayHi(name: "Bob"))
//		assertEquals("hello Bob", e.SAY_HI_1.call(e, [name:"Bob"]) )
//		assertEquals("hello Bob", e.SAY_HI_2.call(e, [name:"Bob"]) )
	} 
	
	/*
	 * e.sayHi("Bob", "hello")
e.sayHi("Bob")
e.sayHi(name: "Bob")
e.sayHi(name: "Bob", greeting: "hello")

or (useful in some cases)

[e1,e2,e3].each { invoke(SAY_HI_1, name: "Bob") }
SAY_HI_1.invoke(e, name:"Bob")  //though this one only resolves at runtime, unless you downcast SAY_HI_1 to AbstractEffector 
	 */
	
}

