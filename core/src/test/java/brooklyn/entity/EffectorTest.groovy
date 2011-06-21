package brooklyn.entity

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;

import org.junit.Ignore;
import org.junit.Test;

import brooklyn.entity.basic.AbstractEffector
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.BasicParameterType

@Retention(RetentionPolicy.RUNTIME)
public @interface ProvidesEffector {
	String name();
	String description() default "sample";
}
@Retention(RetentionPolicy.RUNTIME)
public @interface EffectorParameter {
	String value();
	String description() default "sample"
}
public @interface DefaultValue {
	String value();
}
public @interface Description {
	String value();
}

class EffectorTest {

	public static class EffectorInferredFromAnnotatedMethod {}
	
	public interface CanSayHi {
		static Effector SAY_HI_1 = new AbstractEffector("sayHi", String.class, 
			[
				[ "name", String.class, "person to say hi to" ] as BasicParameterType<String>,
				[ "greeting", String.class, "what to say as greeting", "hello" ] as BasicParameterType<String>
			], 
			"says hello to a person") {
				String call(CanSayHi e, Map m) { e.sayHi(m) }
		};

		static Effector SAY_HI_2 = new EffectorInferredFromAnnotatedMethod(CanSayHi.class, "sayHi");

		public String sayHi( 
			@EffectorParameter("name") String name,
			@EffectorParameter("greeting") @DefaultValue("hello") @Description("what to say") String greeting);

//		String sayHi(String name="");
//		String sayHi();
//		String sayHi(String name);
		
	}
		
	public static class MyEntity extends AbstractEntity implements CanSayHi {
//		{ CanSayHi.SAY_HI.name }
		@ProvidesEffector ( name="sayHi" )
//		@ProvidesEffector ( CanSayHi.SAY_HI.name )
//		@ProvidesEffector( ((AbstractEffector)(CanSayHi.SAY_HI)).name )
//		public String sayHi(String name) { "hello $name" }
		
//		public String sayHi(@ProvidesEffector(name="name") Map flags=[:], String name=flags.name, String greeting=flags.greeting?:"hello") { "$greeting $name" }
		
//		@MakeEffector(description: "says hello to a person")
		public String sayHi(Map flags=[:], String name, String greeting) 
		{ "$greeting $name" }
	}

	@Ignore	
	@Test
	public void testFindEffectors() {
		MyEntity e = new MyEntity();
		println e.sayHi(name: "Bob")
		
		e.getClass().getMethods().each { Method m -> if (m.getName()!="sayHi") return;
			println "method $m: "+(m.getAnnotations());
			println m.getParameterAnnotations()
		}
		println e.SAY_HI
		
		println e.SAY_HI.code.call(e, [name:"Bob"])
	} 
	
}

