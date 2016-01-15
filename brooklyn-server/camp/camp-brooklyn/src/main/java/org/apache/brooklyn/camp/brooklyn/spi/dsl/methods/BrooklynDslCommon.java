/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.camp.brooklyn.spi.dsl.methods;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.TaskAdaptable;
import org.apache.brooklyn.api.mgmt.TaskFactory;
import org.apache.brooklyn.api.objs.Configurable;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampReservedKeys;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeInstantiator;
import org.apache.brooklyn.camp.brooklyn.spi.creation.EntitySpecConfiguration;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.DslUtils;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.DslComponent.Scope;
import org.apache.brooklyn.core.config.external.ExternalConfigSupplier;
import org.apache.brooklyn.core.entity.EntityDynamicType;
import org.apache.brooklyn.core.mgmt.internal.ExternalConfigSupplierRegistry;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.mgmt.persist.DeserializingClassRenamesProvider;
import org.apache.brooklyn.core.sensor.DependentConfiguration;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.ClassCoercionException;
import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.core.task.DeferredSupplier;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.javalang.Reflections;
import org.apache.brooklyn.util.text.StringEscapes.JavaStringEscapes;
import org.apache.brooklyn.util.text.Strings;
import org.apache.commons.beanutils.BeanUtils;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/** static import functions which can be used in `$brooklyn:xxx` contexts */
public class BrooklynDslCommon {

    // Access specific entities

    public static DslComponent entity(String id) {
        return new DslComponent(Scope.GLOBAL, id);
    }
    public static DslComponent parent() {
        return new DslComponent(Scope.PARENT, null);
    }
    public static DslComponent child(String id) {
        return new DslComponent(Scope.CHILD, id);
    }
    public static DslComponent sibling(String id) {
        return new DslComponent(Scope.SIBLING, id);
    }
    public static DslComponent descendant(String id) {
        return new DslComponent(Scope.DESCENDANT, id);
    }
    public static DslComponent ancestor(String id) {
        return new DslComponent(Scope.ANCESTOR, id);
    }
    public static DslComponent root() {
        return new DslComponent(Scope.ROOT, null);
    }
    public static DslComponent scopeRoot() {
        return new DslComponent(Scope.SCOPE_ROOT, null);
    }
    // prefer the syntax above to the below now, but not deprecating the below
    public static DslComponent component(String id) {
        return component("global", id);
    }
    public static DslComponent component(String scope, String id) {
        if (!DslComponent.Scope.isValid(scope)) {
            throw new IllegalArgumentException(scope + " is not a valid scope");
        }
        return new DslComponent(DslComponent.Scope.fromString(scope), id);
    }

    // Access things on entities

    public static BrooklynDslDeferredSupplier<?> config(String keyName) {
        return new DslComponent(Scope.THIS, "").config(keyName);
    }

    public static BrooklynDslDeferredSupplier<?> attributeWhenReady(String sensorName) {
        return new DslComponent(Scope.THIS, "").attributeWhenReady(sensorName);
    }

    /** Returns a {@link Sensor}, looking up the sensor on the context if available and using that,
     * or else defining an untyped (Object) sensor */
    public static BrooklynDslDeferredSupplier<Sensor<?>> sensor(String sensorName) {
        return new DslComponent(Scope.THIS, "").sensor(sensorName);
    }
    
    /** Returns a {@link Sensor} declared on the type (e.g. entity class) declared in the first argument. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Sensor<?> sensor(String clazzName, String sensorName) {
        try {
            // TODO Should use catalog's classloader, rather than Class.forName; how to get that? Should we return a future?!
            String mappedClazzName = DeserializingClassRenamesProvider.findMappedName(clazzName);
            Class<?> clazz = Class.forName(mappedClazzName);
            
            Sensor<?> sensor;
            if (Entity.class.isAssignableFrom(clazz)) {
                sensor = new EntityDynamicType((Class<? extends Entity>) clazz).getSensor(sensorName);
            } else {
                // Some non-entity classes (e.g. ServiceRestarter policy) declare sensors that other
                // entities/policies/enrichers may wish to reference.
                Map<String,Sensor<?>> sensors = EntityDynamicType.findSensors((Class)clazz, null);
                sensor = sensors.get(sensorName);
            }
            if (sensor == null) {
                // TODO could extend API to return a sensor of the given type; useful but makes API ambiguous in theory (unlikely in practise, but still...)
                throw new IllegalArgumentException("Sensor " + sensorName + " not found on class " + clazzName);
            }
            return sensor;
        } catch (ClassNotFoundException e) {
            throw Exceptions.propagate(e);
        }
    }

    // Build complex things

    public static EntitySpecConfiguration entitySpec(Map<String, Object> arguments) {
        return new EntitySpecConfiguration(arguments);
    }

    /**
     * Return an instance of the specified class with its fields set according
     * to the {@link Map} or a {@link BrooklynDslDeferredSupplier} if the arguments are not
     * yet fully resolved.
     */
    @SuppressWarnings("unchecked")
    public static Object object(Map<String, Object> arguments) {
        ConfigBag config = ConfigBag.newInstance(arguments);
        String typeName = BrooklynYamlTypeInstantiator.InstantiatorFromKey.extractTypeName("object", config).orNull();
        Map<String,Object> objectFields = (Map<String, Object>) config.getStringKeyMaybe("object.fields").or(MutableMap.of());
        Map<String,Object> brooklynConfig = (Map<String, Object>) config.getStringKeyMaybe(BrooklynCampReservedKeys.BROOKLYN_CONFIG).or(MutableMap.of());
        try {
            // TODO Should use catalog's classloader, rather than Class.forName; how to get that? Should we return a future?!
            String mappedTypeName = DeserializingClassRenamesProvider.findMappedName(typeName);
            Class<?> type = Class.forName(mappedTypeName);
            
            if (!Reflections.hasNoArgConstructor(type)) {
                throw new IllegalStateException(String.format("Cannot construct %s bean: No public no-arg constructor available", type));
            }
            if ((objectFields.isEmpty() || DslUtils.resolved(objectFields.values())) &&
                    (brooklynConfig.isEmpty() || DslUtils.resolved(brooklynConfig.values()))) {
                return DslObject.create(type, objectFields, brooklynConfig);
            } else {
                return new DslObject(type, objectFields, brooklynConfig);
            }
        } catch (ClassNotFoundException e) {
            throw Exceptions.propagate(e);
        }
    }

    // String manipulation

    /** Return the expression as a literal string without any further parsing. */
    public static Object literal(Object expression) {
        return expression;
    }

    /**
     * Returns a formatted string or a {@link BrooklynDslDeferredSupplier} if the arguments
     * are not yet fully resolved.
     */
    public static Object formatString(final String pattern, final Object...args) {
        if (DslUtils.resolved(args)) {
            // if all args are resolved, apply the format string now
            return String.format(pattern, args);
        } else {
            return new DslFormatString(pattern, args);
        }
    }

    public static Object regexReplacement(final Object source, final Object pattern, final Object replacement) {
        if (DslUtils.resolved(Arrays.asList(source, pattern, replacement))) {
            return (new Functions.RegexReplacer(String.valueOf(pattern), String.valueOf(replacement))).apply(String.valueOf(source));
        } else {
            return new DslRegexReplacement(source, pattern, replacement);
        }
    }

    /**
     * Deferred execution of String formatting.
     *
     * @see DependentConfiguration#formatString(String, Object...)
     */
    protected static class DslFormatString extends BrooklynDslDeferredSupplier<String> {

        private static final long serialVersionUID = -4849297712650560863L;

        private String pattern;
        private Object[] args;

        public DslFormatString(String pattern, Object ...args) {
            this.pattern = pattern;
            this.args = args;
        }

        @Override
        public Task<String> newTask() {
            return DependentConfiguration.formatString(pattern, args);
        }

        @Override
        public String toString() {
            return "$brooklyn:formatString("+
                JavaStringEscapes.wrapJavaString(pattern)+
                (args==null || args.length==0 ? "" : ","+Strings.join(args, ","))+")";
        }
    }



    protected static class DslRegexReplacement extends BrooklynDslDeferredSupplier<String> {

        private Object source;
        private Object pattern;
        private Object replacement;

        public DslRegexReplacement(Object source, Object pattern, Object replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
            this.source = source;
        }

        @Override
        public Task<String> newTask() {
            return DependentConfiguration.regexReplacement(source, pattern, replacement);
        }

        @Override
        public String toString() {
            return String.format("$brooklyn:regexReplace(%s:%s:%s)",source, pattern, replacement);
        }
    }

    /** @deprecated since 0.7.0; use {@link DslFormatString} */
    @SuppressWarnings("serial")
    @Deprecated
    protected static class FormatString extends DslFormatString {
        public FormatString(String pattern, Object[] args) {
            super(pattern, args);
        }
    }

    /** Deferred execution of Object creation. */
    protected static class DslObject extends BrooklynDslDeferredSupplier<Object> {

        private static final long serialVersionUID = 8878388748085419L;

        private Class<?> type;
        private Map<String,Object> fields, config;

        public DslObject(Class<?> type, Map<String,Object> fields,  Map<String,Object> config) {
            this.type = type;
            this.fields = MutableMap.copyOf(fields);
            this.config = MutableMap.copyOf(config);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Task<Object> newTask() {
            List<TaskAdaptable<Object>> tasks = Lists.newLinkedList();
            for (Object value : Iterables.concat(fields.values(), config.values())) {
                if (value instanceof TaskAdaptable) {
                    tasks.add((TaskAdaptable<Object>) value);
                } else if (value instanceof TaskFactory) {
                    tasks.add(((TaskFactory<TaskAdaptable<Object>>) value).newTask());
                }
            }
            Map<String,?> flags = MutableMap.<String,String>of("displayName", "building '"+type+"' with "+tasks.size()+" task"+(tasks.size()!=1?"s":""));
            return DependentConfiguration.transformMultiple(flags, new Function<List<Object>, Object>() {
                        @Override
                        public Object apply(List<Object> input) {
                            Iterator<Object> values = input.iterator();
                            for (String name : fields.keySet()) {
                                Object value = fields.get(name);
                                if (value instanceof TaskAdaptable || value instanceof TaskFactory) {
                                    fields.put(name, values.next());
                                } else if (value instanceof DeferredSupplier) {
                                    fields.put(name, ((DeferredSupplier<?>) value).get());
                                }
                            }
                            for (String name : config.keySet()) {
                                Object value = config.get(name);
                                if (value instanceof TaskAdaptable || value instanceof TaskFactory) {
                                    config.put(name, values.next());
                                } else if (value instanceof DeferredSupplier) {
                                    config.put(name, ((DeferredSupplier<?>) value).get());
                                }
                            }
                            return create(type, fields, config);
                        }
                    }, tasks);
        }

        public static <T> T create(Class<T> type, Map<String,?> fields, Map<String,?> config) {
            try {
                T bean;
                try {
                    bean = (T) TypeCoercions.coerce(fields, type);
                } catch (ClassCoercionException ex) {
                    bean = Reflections.invokeConstructorWithArgs(type).get();
                    BeanUtils.populate(bean, fields);
                }
                if (bean instanceof Configurable && config.size() > 0) {
                    ConfigBag brooklyn = ConfigBag.newInstance(config);
                    FlagUtils.setFieldsFromFlags(bean, brooklyn);
                    FlagUtils.setAllConfigKeys((Configurable) bean, brooklyn, true);
                }
                return bean;
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
        }

        @Override
        public String toString() {
            return "$brooklyn:object(\""+type.getName()+"\")";
        }
    }

    /**
     * Defers to management context's {@link ExternalConfigSupplierRegistry} to resolve values at runtime.
     * The name of the appropriate {@link ExternalConfigSupplier} is captured, along with the key of
     * the desired config value.
     */
    public static DslExternal external(final String providerName, final String key) {
        return new DslExternal(providerName, key);
    }
    protected final static class DslExternal extends BrooklynDslDeferredSupplier<Object> {
        private static final long serialVersionUID = -3860334240490397057L;
        private final String providerName;
        private final String key;

        public DslExternal(String providerName, String key) {
            this.providerName = providerName;
            this.key = key;
        }

        @Override
        public Task<Object> newTask() {
            return Tasks.<Object>builder()
                .displayName("resolving external configuration: '" + key + "' from provider '" + providerName + "'")
                .dynamic(false)
                .body(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        ManagementContextInternal managementContext = DslExternal.managementContext();
                        return managementContext.getExternalConfigProviderRegistry().getConfig(providerName, key);
                    }
                })
                .build();
        }

        @Override
        public String toString() {
            return "$brooklyn:external("+providerName+", "+key+")";
        }
    }

    public static class Functions {
        public static Object regexReplacement(final Object pattern, final Object replacement) {
            if (DslUtils.resolved(pattern, replacement)) {
                return new RegexReplacer(String.valueOf(pattern), String.valueOf(replacement));
            } else {
                return new DslRegexReplacer(pattern, replacement);
            }
        }

        public static class RegexReplacer implements Function<String, String> {
            private final String pattern;
            private final String replacement;

            public RegexReplacer(String pattern, String replacement) {
                this.pattern = pattern;
                this.replacement = replacement;
            }

            @Nullable
            @Override
            public String apply(@Nullable String s) {
                return s == null ? null : Strings.replaceAllRegex(s, pattern, replacement);
            }
        }

        protected static class DslRegexReplacer extends BrooklynDslDeferredSupplier<Function<String, String>> {

            private Object pattern;
            private Object replacement;

            public DslRegexReplacer(Object pattern, Object replacement) {
                this.pattern = pattern;
                this.replacement = replacement;
            }

            @Override
            public Task<Function<String, String>> newTask() {
                return DependentConfiguration.regexReplacement(pattern, replacement);
            }

            @Override
            public String toString() {
                return String.format("$brooklyn:regexReplace(%s:%s)", pattern, replacement);
            }
        }
    }

}
