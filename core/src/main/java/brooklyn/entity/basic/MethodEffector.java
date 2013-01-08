package brooklyn.entity.basic;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.codehaus.groovy.runtime.MethodClosure;

import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.management.internal.EffectorUtils;

import com.google.common.collect.Lists;

/** concrete class for providing an Effector implementation that gets its information from annotations on a method;
 * see Effector*Test for usage example
 */
public class MethodEffector<T> extends AbstractEffector<T> {
    protected static class AnnotationsOnMethod {
        Class<?> clazz;
        String name;
        String description;
        Class<?> returnType;
        List<ParameterType<?>> parameters;
        
        AnnotationsOnMethod(Class<?> clazz, String methodName) {
            name = methodName;
            Method best = null;
            for (Method it : clazz.getMethods()) { 
                if (it.getName().equals(methodName)) {
                    if (best==null || best.getParameterTypes().length < it.getParameterTypes().length) best=it;
                }
            }
            if (best==null) {
                throw new IllegalStateException("Cannot find method "+methodName+" on "+clazz.getCanonicalName());
            }
            Description methodDescriptoinAnnotation = best.getAnnotation(Description.class);
            description = (methodDescriptoinAnnotation != null) ? methodDescriptoinAnnotation.value() : null;
            
            returnType = best.getReturnType();
            parameters = Lists.newArrayList();
            Annotation[][] parameterAnnotations = best.getParameterAnnotations();
            Class<?>[] parameterTypes = best.getParameterTypes();
            for (int i = 0; i < parameterAnnotations.length; i++) {
                Annotation[] anns = parameterAnnotations[i];
                Class<?> type = parameterTypes[i];
                NamedParameter nameAnnotation = findAnnotation(anns, NamedParameter.class);
                Description descriptionAnnotation = findAnnotation(anns, Description.class);
                DefaultValue dvAnnotation = findAnnotation(anns, DefaultValue.class);
                String name = nameAnnotation != null ? nameAnnotation.value() : null; /* ?: "param"+(i+1) */
                String description = descriptionAnnotation != null ? descriptionAnnotation.value() : null;
                Object defaultValue = (dvAnnotation != null) ? TypeCoercions.coerce(dvAnnotation.value(), type) : null;
                BasicParameterType parameterType = new BasicParameterType(name, type, description, defaultValue);
                parameters.add(parameterType);
            }
        }

        public static <T extends Annotation> T findAnnotation(Annotation[] anns, Class<T> type) {
            for (Annotation ann : anns) {
                if (type.isInstance(ann)) return (T) ann;
            }
            return null;
        }
    }

    /** Defines a new effector whose details are supplied as annotations on the given type and method name */
    public MethodEffector(Class<?> whereEffectorDefined, String methodName) {
        this(new AnnotationsOnMethod(whereEffectorDefined, methodName), null);
    }
    
    public MethodEffector(MethodClosure mc) {
        this(new AnnotationsOnMethod((Class)mc.getDelegate(), mc.getMethod()), null);
    }

    /**
     * @deprecated will be deleted in 0.5. Use description annotation
     */
    @Deprecated
    public MethodEffector(Class<?> whereEffectorDefined, String methodName, String description) {
        this(new AnnotationsOnMethod(whereEffectorDefined, methodName), description);
    }
    
    protected MethodEffector(AnnotationsOnMethod anns, String description) {
        super(anns.name, (Class<T>)anns.returnType, anns.parameters, GroovyJavaMethods.<String>elvis(description, anns.description));
    }

    public T call(Entity entity, Map parameters) {
        return (T) ((AbstractEntity)entity).invokeMethod(getName(), EffectorUtils.prepareArgsForEffector(this, parameters));
    }

}
