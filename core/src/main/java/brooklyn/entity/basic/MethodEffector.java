package brooklyn.entity.basic;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.codehaus.groovy.runtime.MethodClosure;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.management.internal.EffectorUtils;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.text.Strings;

import com.google.common.collect.Lists;

/** concrete class for providing an Effector implementation that gets its information from annotations on a method;
 * see Effector*Test for usage example
 */
public class MethodEffector<T> extends AbstractEffector<T> {
    
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
                    LOG.warn("Deprecated use of @Description on effector "+method+"; preferring @Effector annotation's description");
                } else {
                    LOG.warn("Deprecated use of @Description on effector "+method);
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
                    LOG.warn("Deprecated use of @NamedParameter on parameter in effector "+method+"; preferring @EffectorParam annotation's name");
                } else {
                    LOG.warn("Deprecated use of @NamedParameter on parameter in effector "+method);
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
                    LOG.warn("Deprecated use of @Description on parameter in effector "+method+"; preferring @EffectorParam annotation's description");
                } else {
                    LOG.warn("Deprecated use of @Description on parameter in effector "+method);
                }
            }
            String description = (paramDescription != null) ? paramDescription : legacyDescription;

            String paramDefaultValue = (paramAnnotation == null || EffectorParam.MAGIC_STRING_MEANING_NULL.equals(paramAnnotation.defaultValue())) ? null : paramAnnotation.defaultValue();
            String legacyDefaultValue = (dvAnnotation != null) ? dvAnnotation.value() : null;
            if (dvAnnotation != null) {
                if (paramDefaultValue != null && !paramDefaultValue.equals(legacyDefaultValue)) {
                    LOG.warn("Deprecated use of @DefaultValue on parameter in effector "+method+"; preferring @EffectorParam annotation's default value");
                } else {
                    LOG.warn("Deprecated use of @DefaultValue on parameter in effector "+method);
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
            return EffectorUtils.invokeEffector(entity, this, parametersArray);
        } else {
            // TODO Should really find method with right signature, rather than just the right args.
            // TODO prepareArgs can miss things out that have "default values"! Code below will probably fail if that happens.
            Method[] methods = entity.getClass().getMethods();
            for (Method method : methods) {
                if (method.getName().equals(getName()) && parametersArray.length == method.getParameterTypes().length) {
                    try {
                        return (T) method.invoke(entity, parametersArray);
                    } catch (Exception e) {
                        throw new RuntimeException("Error invoking effector "+this+" on entity "+entity, e);
                    }
                }
            }
            throw new IllegalStateException("Could not find method for effector "+getName()+" with "+parametersArray.length+" parameters on "+entity);
        }
    }
}
