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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.camp.brooklyn.BrooklynCampReservedKeys;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynYamlTypeInstantiator;
import org.apache.brooklyn.camp.brooklyn.spi.creation.EntitySpecConfiguration;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslDeferredSupplier;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.DslUtils;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.methods.DslComponent.Scope;
import org.apache.brooklyn.management.Task;
import org.apache.brooklyn.management.TaskAdaptable;
import org.apache.brooklyn.management.TaskFactory;
import org.apache.commons.beanutils.BeanUtils;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityDynamicType;
import brooklyn.entity.trait.Configurable;
import brooklyn.event.Sensor;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.ClassCoercionException;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.task.DeferredSupplier;
import brooklyn.util.text.StringEscapes.JavaStringEscapes;
import brooklyn.util.text.Strings;

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
            Class<?> clazz = Class.forName(clazzName);
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
            Class<?> type = Class.forName(typeName);
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

}
