package brooklyn.entity.basic;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.codehaus.groovy.runtime.MethodClosure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.management.internal.EffectorUtils;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.PropagatedRuntimeException;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.text.Strings;

import com.google.common.collect.Lists;

/** concrete class for providing an Effector implementation that gets its information from annotations on a method;
 * see Effector*Test for usage example.
 * <p>
 * note that the method must be on an interface in order for it to be remoted, with the current implementation.
 * see comments in {@link #call(Entity, Map)} for more details.
 */
public class MethodEffector<T> extends AbstractEffector<T> {

    private static final Logger log = LoggerFactory.getLogger(MethodEffector.class);
    
    @SuppressWarnings("rawtypes")
    public static Effector<?> create(Method m) {
        return new MethodEffector(m);
    }
    
    protected static class AnnotationsOnMethod {
        final Class<?> clazz;
        final String name;
        final String description;
        final Class<?> returnType;
        final List<ParameterType<?>> parameters;

        public AnnotationsOnMethod(Class<?> clazz, String methodName) {
            this(clazz, inferBestMethod(clazz, methodName));
        }

        public AnnotationsOnMethod(Class<?> clazz, Method method) {
            this.clazz = clazz;
            this.name = method.getName();
            this.returnType = method.getReturnType();

            // Get the description
            brooklyn.entity.annotation.Effector effectorAnnotation = method.getAnnotation(brooklyn.entity.annotation.Effector.class);
            Description methodDescriptionAnnotation = method.getAnnotation(Description.class);
            String effDescription = (effectorAnnotation != null) ? effectorAnnotation.description() : null;
            String effLegacyDescription = (methodDescriptionAnnotation != null) ? methodDescriptionAnnotation.value() : null;

            if (methodDescriptionAnnotation != null) {
                if (!Strings.isEmpty(effDescription) && !effDescription.equals(effLegacyDescription)) {
                    log.warn("Deprecated use of @Description on effector "+method+"; preferring @Effector annotation's description");
                } else {
                    log.warn("Deprecated use of @Description on effector "+method);
                }
            }
            description = (Strings.isEmpty(effDescription)) ? effLegacyDescription : effDescription;

            // Get the parameters
            parameters = Lists.newArrayList();
            int numParameters = method.getParameterTypes().length;
            for (int i = 0; i < numParameters; i++) {
                parameters.add(toParameterType(method, i));
            }
        }

        protected static ParameterType<?> toParameterType(Method method, int paramIndex) {
            Annotation[] anns = method.getParameterAnnotations()[paramIndex];
            Class<?> type = method.getParameterTypes()[paramIndex];
            EffectorParam paramAnnotation = findAnnotation(anns, EffectorParam.class);
            NamedParameter nameAnnotation = findAnnotation(anns, NamedParameter.class);
            Description descriptionAnnotation = findAnnotation(anns, Description.class);
            DefaultValue dvAnnotation = findAnnotation(anns, DefaultValue.class);

            String paramName = (paramAnnotation == null) ? null : paramAnnotation.name();
            String legacyName = (nameAnnotation != null) ? nameAnnotation.value() : null;
            if (nameAnnotation != null) {
                if (paramName != null && paramName.equals(legacyName)) {
                    log.warn("Deprecated use of @NamedParameter on parameter in effector "+method+"; preferring @EffectorParam annotation's name");
                } else {
                    log.warn("Deprecated use of @NamedParameter on parameter in effector "+method);
                }
            }
            // TODO if blank, could do "param"+(i+1); would that be better?
            // TODO this will now give "" if name is blank, rather than previously null. Is that ok?!
            String name = (paramAnnotation != null) ? paramAnnotation.name() : 
                    (nameAnnotation != null ? nameAnnotation.value() : null);

            String paramDescription = (paramAnnotation == null || EffectorParam.MAGIC_STRING_MEANING_NULL.equals(paramAnnotation.description())) ? null : paramAnnotation.description();
            String legacyDescription = (descriptionAnnotation != null) ? descriptionAnnotation.value() : null;
            if (descriptionAnnotation != null) {
                if (paramDescription != null && !paramDescription.equals(legacyDescription)) {
                    log.warn("Deprecated use of @Description on parameter in effector "+method+"; preferring @EffectorParam annotation's description");
                } else {
                    log.warn("Deprecated use of @Description on parameter in effector "+method);
                }
            }
            String description = (paramDescription != null) ? paramDescription : legacyDescription;

            String paramDefaultValue = (paramAnnotation == null || EffectorParam.MAGIC_STRING_MEANING_NULL.equals(paramAnnotation.defaultValue())) ? null : paramAnnotation.defaultValue();
            String legacyDefaultValue = (dvAnnotation != null) ? dvAnnotation.value() : null;
            if (dvAnnotation != null) {
                if (paramDefaultValue != null && !paramDefaultValue.equals(legacyDefaultValue)) {
                    log.warn("Deprecated use of @DefaultValue on parameter in effector "+method+"; preferring @EffectorParam annotation's default value");
                } else {
                    log.warn("Deprecated use of @DefaultValue on parameter in effector "+method);
                }
            }
            Object defaultValue = (paramDefaultValue != null) ? 
                    TypeCoercions.coerce(paramDefaultValue, type) :
                    (legacyDefaultValue != null ? TypeCoercions.coerce(legacyDefaultValue, type) : null);

            return new BasicParameterType(name, type, description, defaultValue);
        }
        
        protected static <T extends Annotation> T findAnnotation(Annotation[] anns, Class<T> type) {
            for (Annotation ann : anns) {
                if (type.isInstance(ann)) return (T) ann;
            }
            return null;
        }
        
        protected static Method inferBestMethod(Class<?> clazz, String methodName) {
            Method best = null;
            for (Method it : clazz.getMethods()) { 
                if (it.getName().equals(methodName)) {
                    if (best==null || best.getParameterTypes().length < it.getParameterTypes().length) best=it;
                }
            }
            if (best==null) {
                throw new IllegalStateException("Cannot find method "+methodName+" on "+clazz.getCanonicalName());
            }
            return best;
        }
    }

    /** Defines a new effector whose details are supplied as annotations on the given type and method name */
    public MethodEffector(Class<?> whereEffectorDefined, String methodName) {
        this(new AnnotationsOnMethod(whereEffectorDefined, methodName), null);
    }

    public MethodEffector(Method method) {
        this(new AnnotationsOnMethod(method.getDeclaringClass(), method), null);
    }

    public MethodEffector(MethodClosure mc) {
        this(new AnnotationsOnMethod((Class)mc.getDelegate(), mc.getMethod()), null);
    }

    protected MethodEffector(AnnotationsOnMethod anns, String description) {
        super(anns.name, (Class<T>)anns.returnType, anns.parameters, GroovyJavaMethods.<String>elvis(description, anns.description));
    }

    public T call(Entity entity, Map parameters) {
        Object[] parametersArray = EffectorUtils.prepareArgsForEffector(this, parameters);
        if (entity instanceof AbstractEntity) {
            return EffectorUtils.invokeMethodEffector(entity, this, parametersArray);
        } else {
            // we are dealing with a proxy here
            // this implementation invokes the method on the proxy
            // (requiring it to be on the interface)
            // and letting the proxy deal with the remoting / runAtEntity;
            // alternatively we could create the task here and pass it to runAtEntity;
            // the latter may allow us to simplify/remove a lot of the stuff from 
            // EffectorUtils and possibly Effectors and Entities
            
            // TODO Should really find method with right signature, rather than just the right args.
            // TODO prepareArgs can miss things out that have "default values"! Code below will probably fail if that happens.
            Method[] methods = entity.getClass().getMethods();
            for (Method method : methods) {
                if (method.getName().equals(getName())) {
                    if (parametersArray.length == method.getParameterTypes().length) {
                        try {
                            return (T) method.invoke(entity, parametersArray);
                        } catch (Exception e) {
                            // exception handled by the proxy invocation (which leads to EffectorUtils.invokeEffectorMethod...)
                            throw Exceptions.propagate(e);
                        }
                    }
                }
            }
            String msg = "Could not find method for effector "+getName()+" with "+parametersArray.length+" parameters on "+entity;
            log.warn(msg+" (throwing); available methods are: "+Arrays.toString(methods));
            throw new IllegalStateException(msg);
        }
    }
    /*

     */
}
