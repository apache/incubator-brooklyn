package brooklyn.entity.basic;

import groovy.transform.InheritConstructors;

import java.lang.annotation.Annotation
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.reflect.Method
import java.util.Collections
import java.util.List

import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.runtime.MethodClosure;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.ParameterType
import brooklyn.util.internal.LanguageUtils

import com.google.common.base.Objects

/**
 * The abstract {@link Effector} implementation.
 * 
 * The concrete subclass (often anonymous) will supply the {@link #call(EntityType, Map)} implementation,
 * and the fields in the constructor.
 */
public abstract class AbstractEffector<T> implements Effector<T> {
    public static final Logger LOG = LoggerFactory.getLogger(Effector)

    private static final long serialVersionUID = 1832435915652457843L;

    private final String name;
    private final Class<T> returnType;
    private final List<ParameterType<?>> parameters;
    private final String description;

    private AbstractEffector() { /* for gson */ name = null; }

    public AbstractEffector(String name, Class<T> returnType, List<ParameterType<?>> parameters, String description) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = Collections.unmodifiableList(parameters);
        this.description = description;
        setMetaClass(DefaultGroovyMethods.getMetaClass(getClass()))
    }

    public String getName() {
        return name;
    }

    public Class<T> getReturnType() {
        return returnType;
    }

    public String getReturnTypeName() {
        return returnType.getCanonicalName();
    }

    public List<ParameterType<?>> getParameters() {
        return parameters;
    }

    public String getDescription() {
        return description;
    }

    public abstract T call(Entity entity, Map parameters);

    /** Convenience for named-parameter syntax (needs map in first argument) */
    public T call(Map parameters=[:], Entity entity) { call(entity, parameters); }

    @Override
    public String toString() {
        return name+"["+parameters.collect({it.name}).join(",")+"]";
    }

    @Override
    public int hashCode() {
        Objects.hashCode(description, name, parameters, returnType);
    }

    @Override
    public boolean equals(Object obj) {
        LanguageUtils.equals(this, obj, ["description", "name", "parameters", "returnType"]);
    }

    /** takes an array of arguments, which typically contain a map in the first position (and possibly nothing else),
    * and returns an array of arguments suitable for use by Effector according to the ParameterTypes it exposes */
   public static Object prepareArgsForEffector(Effector eff, Object args) {
       //attempt to coerce unexpected types
       if (args==null) args = [:]
       if (!args.getClass().isArray()) {
           if (args instanceof Collection) args = args as Object[]
           else args = [ args ] as Object[]
       }

       //if args starts with a map, assume it contains the named arguments
       //(but only use it when we have insufficient supplied arguments)
       List l = new ArrayList()
       l.addAll(args)
       Map m = (args.size() > 0 && args[0] instanceof Map ? new LinkedHashMap(l.remove(0)) : null)
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

}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
/** provides a runtime name of a paramter, esp for effectors; typically matches the name in the code */
public @interface NamedParameter {
    String value();
}
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
/** provides runtime access to the name of a paramter, esp for effectors; typically matches any default value supplied in the code */
public @interface DefaultValue {
    String value();
}
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.PARAMETER, ElementType.METHOD])
/** provides runtime access to the description of an effector or paramter, esp for effectors */
public @interface Description {
    String value();
}

/** concrete class for providing an Effector implementation that gets its information from annotations on a method;
 * see Effector*Test for usage example
 */
public class MethodEffector<T> extends AbstractEffector<T> {
    protected static class AnnotationsOnMethod {
        Class<?> clazz;
        String name;
		String description;
        Class<?> returnType;
        List parameters;
        AnnotationsOnMethod(Class<?> clazz, String methodName) {
            name = methodName;
            Method best = null;
            clazz.getMethods().each { if (it.getName()==methodName) {
                if (best==null || best.getParameterTypes().length < it.getParameterTypes().length) best=it;
            } }
            if (best==null) throw new IllegalStateException("Cannot find method $methodName on "+clazz.getCanonicalName());
            description = best.getAnnotation(Description)?.value()
            returnType = best.getReturnType()
            parameters = []
            LanguageUtils.forBothWithIndex(best.getParameterAnnotations(), best.getParameterTypes()) {
                anns, type, i -> def m = [
                    name:findAnnotation(anns, NamedParameter)?.value() /* ?: "param"+(i+1) */ ,
                    type:type,
                    description:findAnnotation(anns, Description)?.value() ]
                def dv = findAnnotation(anns, DefaultValue);
                if (dv) m.defaultValue = dv.value().asType(type)
                parameters.add(new BasicParameterType(m))
            }
        }

        public static <T extends Annotation> T findAnnotation(Annotation[] anns, Class<T> type) {
            anns.find { type.isInstance(it) }
        }
    }

	/** Defines a new effector whose details are supplied as annotations on the given type and method name */
	public MethodEffector(Class<?> whereEffectorDefined, String methodName) {
		this(new AnnotationsOnMethod(whereEffectorDefined, methodName), null);
	}
	public MethodEffector(MethodClosure mc) {
		this(new AnnotationsOnMethod(mc.delegate, mc.method), null);
	}

    /**
     * @deprecated will be deleted in 0.5. Use description annotation
     */
    @Deprecated
    public MethodEffector(Class<?> whereEffectorDefined, String methodName, String description) {
        this(new AnnotationsOnMethod(whereEffectorDefined, methodName), description);
    }
    protected MethodEffector(AnnotationsOnMethod anns, String description) {
        super(anns.name, anns.returnType, anns.parameters, description?:anns.description);
    }

    public T call(Entity entity, Map parameters) {
        entity.invokeMethod(name, prepareArgsForEffector(this, parameters));
    }

}

public abstract class ExplicitEffector<I,T> extends AbstractEffector<T> {
    public ExplicitEffector(String name, Class<T> type, List<ParameterType<?>> parameters=[], String description) {
        super(name, type, parameters, description)
    }

	public T call(Entity entity, Map parameters) {
        invokeEffector((I) entity, parameters );
    }

    public abstract T invokeEffector(I trait, Map<String,?> parameters);
	
	/** convenience to create an effector supplying a closure; annotations are preferred,
	 * and subclass here would be failback, but this is offered as 
	 * workaround for bug GROOVY-5122, as discussed in test class CanSayHi 
	 */
	public static ExplicitEffector<I,T> create(String name, Class<T> type, List<ParameterType<?>> parameters, String description, Closure body) {
		return new ExplicitEffectorFromClosure(name, type, parameters, description, body);
	}
}

private class ExplicitEffectorFromClosure<I,T> extends ExplicitEffector<I,T> {
	final Closure body
	public ExplicitEffectorFromClosure(String name, Class<T> type, List<ParameterType<?>> parameters, String description, Closure body) {
		super(name, type, parameters, description)
		this.body = body
	}
	public T invokeEffector(I trait, Map<String,?> parameters) { body.call(trait, parameters) }
}

/**
 * @deprecated will be deleted in 0.5.  now called MethodEffector.
 */
@Deprecated
public class EffectorInferredFromAnnotatedMethod<T> extends MethodEffector<T> {
	public EffectorInferredFromAnnotatedMethod(Class<?> whereEffectorDefined, String methodName) {
		super(whereEffectorDefined, methodName);
	}
	public EffectorInferredFromAnnotatedMethod(Class<?> whereEffectorDefined, String methodName, String description) {
		super(whereEffectorDefined, methodName, description);
	}
}

/**
 * @deprecated will be deleted in 0.5. Now called ExplicitEffector.
 */
@Deprecated
public abstract class EffectorWithExplicitImplementation<I,T> extends ExplicitEffector<I,T> {
	public EffectorWithExplicitImplementation(String name, Class<T> type, List<ParameterType<?>> parameters=[], String description) {
        super(name, type, parameters, description)
    }
}
