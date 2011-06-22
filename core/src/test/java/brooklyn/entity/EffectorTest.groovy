package brooklyn.entity

import static org.junit.Assert.*

import java.lang.reflect.Field

import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractEffector
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.basic.DefaultValue
import brooklyn.entity.basic.Description
import brooklyn.entity.basic.EffectorInferredFromAnnotatedMethod
import brooklyn.entity.basic.NamedParameter


class EffectorTest {
	private static final Logger log = LoggerFactory.getLogger(EffectorTest.class);
	// FIXME remove this when we have a process for setting logging...
//	static {
//		log.metaClass {
//			warn = { String a -> println "WARN "+a }
//			warn = { String a, Throwable t -> println "WARN "+a; t.printStackTrace(); }
//			info = { String a -> println "INFO "+a }
//		}
//	}
	
	public interface CanSayHi {
		static Effector<String> SAY_HI_1 = new AbstractEffector<CanSayHi,String>("sayHi1", String.class, 
			[
				[ "name", String.class, "person to say hi to" ] as BasicParameterType<String>,
				[ "greeting", String.class, "what to say as greeting", "hello" ] as BasicParameterType<String>
			], 
			"says hello to a person") {
				public String call(CanSayHi e, Map m) { e.sayHi1(m) }
		};

		static Effector<String> SAY_HI_2 = new EffectorInferredFromAnnotatedMethod<String>(CanSayHi.class, "sayHi2", "says hello");

		public String sayHi1(String name, String greeting);
		public String sayHi2(
			@NamedParameter("name") String name,
			@NamedParameter("greeting") @DefaultValue("hello") @Description("what to say") String greeting);
	}
		
	public static class MyEntity extends AbstractEntity implements CanSayHi, GroovyInterceptable {
		public String sayHi1(String name, String greeting) { "$greeting $name" }
		public String sayHi2(String name, String greeting) { "$greeting $name" }

		//TODO move stuff below here into AbstractEntity :)
		
		private boolean invokeMethodPrep = false
		
		public Object invokeMethod(String name, Object args) {
			if (!this.@invokeMethodPrep) {
				this.@invokeMethodPrep = true;
				
				//args should be an array, warn if we got here wrongly
				if (args==null) log.warn("$this.$name invoked with incorrect args signature (null)", new Throwable("source of incorrect invocation of $this.$name"))
				else if (!args.getClass().isArray()) log.warn("$this.$name invoked with incorrect args signature (non-array ${args.getClass()}): "+args, new Throwable("source of incorrect invocation of $this.$name"))
				
				try {
					Effector eff = getEffectors().get(name) 
					if (eff) args = prepareArgsForEffector(eff, args);
				} finally { this.@invokeMethodPrep = false; }
			}
			metaClass.invokeMethod(this, name, args);
			//following is recommended on web site, but above is how groovy actually implements it
//			def metaMethod = metaClass.getMetaMethod(name, newArgs)
//			if (metaMethod==null)
//				throw new IllegalArgumentException("Invalid arguments (no method found) for method $name: "+newArgs);
//			metaMethod.invoke(this, newArgs)
		}
		private transient volatile Map<String,Effector> effectors = null
		public Map<String,Effector> getEffectors() {
			if (effectors!=null) return effectors
			synchronized (this) {
				if (effectors!=null) return effectors
				Map<String,Effector> effectorsT = [:]
				getClass().getFields().each { Field f ->
					if (Effector.class.isAssignableFrom(f.getType())) {
						Effector eff = f.get(this)
						def overwritten = effectorsT.put(eff.name, eff)
						if (overwritten!=null) log.warn("multiple definitions for effector ${eff.name} on $this; preferring $eff to $overwritten")
					}
				}
				effectors = effectorsT
			}
		}
		private Object prepareArgsForEffector(Effector eff, Object args) {
			//if args starts with a map, assume it contains the named arguments
			//(but only use it when we have insufficient supplied arguments)
			List l = new ArrayList()
			l.addAll(args)
			Map m = (args[0] instanceof Map ? new LinkedHashMap(l.remove(0)) : null)
			def newArgs = []
			int newArgsNeeded = eff.getParameters().size()
			boolean mapUsed = false;
			eff.getParameters().eachWithIndex { ParameterType<?> it, int index ->
				if (l.size()>=newArgsNeeded)
					//all supplied (unnamed) arguments must be used; ignore map
					newArgs << l.remove(0)
				else if (m && it.name && m.containsKey(it.name))
					//some arguments were not supplied, and this one is in the map
					newArgs << m.remove(it.name)
				else if (index==0 && Map.class.isAssignableFrom(it.getParameterClass())) {
					//if first arg is a map it takes the supplied map
					newArgs << m
					mapUsed = true
				} else if (!l.isEmpty() && it.getParameterClass().isInstance(l[0]))
					//if there are parameters supplied, and type is correct, they get applied before default values
					//(this is akin to groovy)
					newArgs << l.remove(0)
				else if (it in BasicParameterType && it.hasDefaultValue())
					//finally, default values are used to make up for missing parameters
					newArgs << it.defaultValue
				else
					throw new IllegalArgumentException("Invalid arguments (count mismatch) for effector $eff: "+args);
					
				newArgsNeeded--
			}
			if (newArgsNeeded>0)
				throw new IllegalArgumentException("Invalid arguments (missing $newArgsNeeded) for effector $eff: "+args);
			if (!l.isEmpty())
				throw new IllegalArgumentException("Invalid arguments (${l.size()} extra) for effector $eff: "+args);
			if (m && !mapUsed)
				throw new IllegalArgumentException("Invalid arguments (${m.size()} extra named) for effector $eff: "+args);
			newArgs = newArgs as Object[]
		}
		
		public <T> T invoke(Map parameters=[:], Effector<T> eff) {
			eff.call(this, parameters);
		}
		//add'l form supplied for when map needs to be made explicit (above supports implicit named args)
		public <T> T invoke(Effector<T> eff, Map parameters) {
			eff.call(this, parameters);
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
		assertEquals("hello Bob", e.invoke(e.SAY_HI_1, [name:"Bob"]) );
	} 
	@Test
	public void testInvokeEffectors2() {
		MyEntity e = new MyEntity();
		
		assertEquals("hi Bob", e.sayHi2("Bob", "hi"))
		assertEquals("hello Bob", e.sayHi2("Bob"))
		
		assertEquals("hi Bob", e.sayHi2(name: "Bob", greeting:"hi"))
		assertEquals("hello Bob", e.sayHi2(name: "Bob"))
		
		assertEquals("hello Bob", e.SAY_HI_2.call(e, [name:"Bob"]) )
		assertEquals("hello Bob", e.invoke(e.SAY_HI_2, [name:"Bob"]) );
	}

	//TODO test spread invocation:
//	[e1,e2,e3]*.invoke(SAY_HI_1, name: "Bob")

	//TODO test edge/error conditions
	//(missing parameters, wrong number of params, etc)	
	
}

