package brooklyn.entity.basic;

import groovy.transform.InheritConstructors;

import java.lang.annotation.Annotation
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.util.Collections
import java.util.List
import java.util.concurrent.Callable

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.ParameterType
import brooklyn.util.internal.LanguageUtils

public abstract class AbstractEffector<E, T> implements Effector<E, T> {
    private static final long serialVersionUID = 1832435915652457843L;
    
    final private String name;
    private Class<T> returnType;
    private List<ParameterType<?>> parameters;
    private String description;
	
//    @SuppressWarnings("unused")
    private AbstractEffector() { /* for gson */ name = null; }
    
    public AbstractEffector(String name, Class<T> returnType, List<ParameterType<?>> parameters, String description) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = Collections.unmodifiableList(parameters);
        this.description = description;
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
    
	public abstract T call(E entity, Map parameters);

	/** convenience for named-parameter syntax (needs map in first argument) */
	public T call(Map parameters=[:], E entity) { call(entity, parameters); }

	@Override
	public String toString() {
		return name+"["+parameters.collect({it.name}).join(",")+"]";
	}
	
	@Override
	public int hashCode() {
        // ENGR-1560 use Objects.hashCode(a, b, c, d)
		final int prime = 31;
		int result = 1;
		result = prime * result + ((description == null) ? 0 : description.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((parameters == null) ? 0 : parameters.hashCode());
		result = prime * result + ((returnType == null) ? 0 : returnType.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractEffector other = (AbstractEffector) obj;
		if (description == null) {
			if (other.description != null)
				return false;
		} else if (!description.equals(other.description))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (parameters == null) {
			if (other.parameters != null)
				return false;
		} else if (!parameters.equals(other.parameters))
			return false;
		if (returnType == null) {
			if (other.returnType != null)
				return false;
		} else if (!returnType.equals(other.returnType))
			return false;
		return true;
	}
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface NamedParameter {
	String value();
}
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface DefaultValue {
	String value();
}
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Description {
	String value();
}

public class EffectorInferredFromAnnotatedMethod<E, T> extends AbstractEffector<E, T> {
	protected static class AnnotationsOnMethod {
		Class<?> clazz;
		String name;
		Class<?> returnType;
		List parameters;
		AnnotationsOnMethod(Class<?> clazz, String methodName) {
			name = methodName;
			Method best = null;
			clazz.getMethods().each { if (it.getName()==methodName) {
				if (best==null || best.getParameterTypes().length < it.getParameterTypes().length) best=it;
			} }
			if (best==null) throw new IllegalStateException("Cannot find method $methodName on "+clazz.getCanonicalName());
			returnType = best.getReturnType()
			parameters = []
			LanguageUtils.forBothWithIndex(best.getParameterAnnotations(), best.getParameterTypes()) {
				anns, type, i -> def m = [
					name: findAnnotation(anns, NamedParameter)?.value() /* ?: "param"+(i+1) */ , 
					type: type,
					description: findAnnotation(anns, Description)?.value() ]
				def dv = findAnnotation(anns, DefaultValue);
				if (dv) m.defaultValue = dv.value()
				parameters.add(new BasicParameterType(m))
			}
		}

		public static <T extends Annotation> T findAnnotation(Annotation[] anns, Class<T> type) { 
			anns.find { type.isInstance(it) } 
		}		
	}
	
	public EffectorInferredFromAnnotatedMethod(Class<?> whereEffectorDefined, String methodName, String description=null) {
		this(new AnnotationsOnMethod(whereEffectorDefined, methodName), description);
	}
	protected EffectorInferredFromAnnotatedMethod(AnnotationsOnMethod anns, String description) {
		super(anns.name, anns.returnType, anns.parameters, description);
	}
	
	public T call(E entity, Map parameters) {
		entity."$name"(parameters);
	}
}

/**
 * TODO attempting to add an interface type parameter
 */
@InheritConstructors
public abstract class InterfaceEffector<E, T> extends AbstractEffector<E, T> { }
