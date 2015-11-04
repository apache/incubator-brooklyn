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
package org.apache.brooklyn.core.objs;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.BrooklynType;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.StringPredicates;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

public class BasicSpecParameter<T> implements SpecParameter<T>{
    private static final long serialVersionUID = -4728186276307619778L;

    private String label;
    private boolean pinned;
    private ConfigKey<T> type;

    public BasicSpecParameter(String label, boolean pinned, ConfigKey<T> type) {
        this.label = label;
        this.pinned = pinned;
        this.type = type;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public boolean isPinned() {
        return pinned;
    }

    @Override
    public ConfigKey<T> getType() {
        return type;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(label, pinned, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BasicSpecParameter<?> other = (BasicSpecParameter<?>) obj;
        return Objects.equal(label,  other.label) &&
                pinned == other.pinned &&
                Objects.equal(type, other.type);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("label", label)
                .add("pinned", pinned)
                .add("type", type)
                .toString();
    }

    // Not CAMP specific since it's used to get the parameters from catalog items meta which are syntax independent.
    public static List<SpecParameter<?>> fromConfigList(List<?> obj, BrooklynClassLoadingContext loader) {
        return ParseYamlInputs.parseParameters(obj, loader);
    }

    public static List<SpecParameter<?>> fromClass(ManagementContext mgmt, Class<?> type) {
        return ParseClassParameters.parseParameters(getImplementedBy(mgmt, type));
    }

    public static List<SpecParameter<?>> fromSpec(ManagementContext mgmt, AbstractBrooklynObjectSpec<?, ?> spec) {
        if (!spec.getParameters().isEmpty()) {
            return spec.getParameters();
        }
        Class<?> type = null;
        if (spec instanceof EntitySpec) {
            EntitySpec<?> entitySpec = (EntitySpec<?>)spec;
            if (entitySpec.getImplementation() != null) {
                type = entitySpec.getImplementation();
            }
        }
        if (type == null) {
            type = getImplementedBy(mgmt, spec.getType());
        }
        return ParseClassParameters.parseParameters(getImplementedBy(mgmt, type));
    }

    private static Class<?> getImplementedBy(ManagementContext mgmt, Class<?> type) {
        if (Entity.class.isAssignableFrom(type) && type.isInterface()) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends Entity> uncheckedType = ParseClassParameters.getEntityImplementedBy(mgmt, (Class<Entity>)type);
                return uncheckedType;
            } catch(IllegalArgumentException e) {
                // NOP, no implementation for type, use whatever we have
            }
        }
        return type;
    }

    private static final class ParseYamlInputs {
        private static final String DEFAULT_TYPE = "string";
        private static final Map<String, Class<?>> BUILT_IN_TYPES = ImmutableMap.<String, Class<?>>builder()
                .put(DEFAULT_TYPE, String.class)
                .put("integer", Integer.class)
                .put("long", Long.class)
                .put("float", Float.class)
                .put("double", Double.class)
                .put("timestamp", Date.class)
                .put("port", PortRange.class)
                .build();

        private static final Map<String, Predicate<?>> BUILT_IN_CONSTRAINTS = ImmutableMap.<String, Predicate<?>>of(
                "required", StringPredicates.isNonBlank());

        public static List<SpecParameter<?>> parseParameters(List<?> inputsRaw, BrooklynClassLoadingContext loader) {
            if (inputsRaw == null) return ImmutableList.of();
            List<SpecParameter<?>> inputs = new ArrayList<>(inputsRaw.size());
            for (Object obj : inputsRaw) {
                inputs.add(parseParameter(obj, loader));
            }
            return inputs;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private static SpecParameter<?> parseParameter(Object obj, BrooklynClassLoadingContext loader) {
            Map inputDef;
            if (obj instanceof String) {
                inputDef = ImmutableMap.of("name", obj);
            } else if (obj instanceof Map) {
                inputDef = (Map) obj;
            } else {
                throw new IllegalArgumentException("Catalog input definition expected to be a map, but is " + obj.getClass() + " instead: " + obj);
            }
            String name = (String)inputDef.get("name");
            String label = (String)inputDef.get("label");
            String description = (String)inputDef.get("description");
            String type = (String)inputDef.get("type");
            Object defaultValue = inputDef.get("default");
            Predicate<?> constraints = parseConstraints(inputDef.get("constraints"), loader);

            if (name == null) {
                throw new IllegalArgumentException("'name' value missing from input definition " + obj + " but is required. Check for typos.");
            }

            ConfigKey inputType = BasicConfigKey.builder(inferType(type, loader))
                    .name(name)
                    .description(description)
                    .defaultValue(defaultValue)
                    .constraint(constraints)
                    .build();
            return new BasicSpecParameter(Objects.firstNonNull(label, name), true, inputType);
        }

        @SuppressWarnings({ "rawtypes" })
        private static TypeToken inferType(String typeRaw, BrooklynClassLoadingContext loader) {
            if (typeRaw == null) return TypeToken.of(String.class);
            String type = typeRaw.trim();
            if (BUILT_IN_TYPES.containsKey(type)) {
                return TypeToken.of(BUILT_IN_TYPES.get(type));
            } else {
                // Assume it's a Java type
                Maybe<Class<?>> inputType = loader.tryLoadClass(type);
                if (inputType.isPresent()) {
                    return TypeToken.of(inputType.get());
                } else {
                    throw new IllegalArgumentException("The type '" + type + "' for a catalog input not recognised as a built-in (" + BUILT_IN_TYPES.keySet() + ") or a java type");
                }
            }
        }
    
        @SuppressWarnings({ "unchecked", "rawtypes" })
        private static Predicate parseConstraints(Object obj, BrooklynClassLoadingContext loader) {
            List constraintsRaw;
            if (obj == null) {
                constraintsRaw = ImmutableList.of();
            } else if (obj instanceof String) {
                constraintsRaw = ImmutableList.of(obj);
            } else if (obj instanceof List) {
                constraintsRaw = (List) obj;
            } else {
                throw new IllegalArgumentException ("The constraint '" + obj + "' for a catalog input is invalid format - string or list supported");
            }
            List<Predicate> constraints = new ArrayList(constraintsRaw.size());
            for (Object untypedConstraint : constraintsRaw) {
                String constraint = (String)untypedConstraint;
                if (BUILT_IN_CONSTRAINTS.containsKey(constraint)) {
                    constraints.add(BUILT_IN_CONSTRAINTS.get(constraint));
                } else {
                    throw new IllegalArgumentException("The constraint '" + constraint + "' for a catalog input is not recognized as a built-in (" + BUILT_IN_CONSTRAINTS.keySet() + ")");
                }
            }
            if (!constraints.isEmpty()) {
                if (constraints.size() == 1) {
                    return constraints.get(0);
                } else {
                    return Predicates.and((List<Predicate<Object>>)(List) constraints);
                }
            } else {
                return Predicates.alwaysTrue();
            }
        }
    }

    private static final class ParseClassParameters {
        private static final class WeightedParameter {
            private Double weight;
            private SpecParameter<?> input;
            public WeightedParameter(Double weight, SpecParameter<?> input) {
                this.weight = weight;
                this.input = input;
            }
            public Double getWeight() {return weight; }
            public SpecParameter<?> getInput() { return input; }
        }
        private static final class WeightedParameterComparator implements Comparator<WeightedParameter> {
            @Override
            public int compare(WeightedParameter o1, WeightedParameter o2) {
                if (o1.getWeight() == null && o2.getWeight() == null) {
                    return o1.getInput().getLabel().compareTo(o2.getInput().getLabel());
                } else if (o1.getWeight() == null) {
                    return 1;
                } else if (o2.getWeight() == null) {
                    return -1;
                } else {
                    return -Double.compare(o1.getWeight(),  o2.getWeight());
                }
            }
        }
        private static final class SpecParameterTransformer implements Function<WeightedParameter, SpecParameter<?>> {
            @Override
            public SpecParameter<?> apply(WeightedParameter input) {
                return input.getInput();
            }
        }

        public static List<SpecParameter<?>> parseParameters(Class<?> c) {
            MutableList<WeightedParameter> parameters = MutableList.<WeightedParameter>of();
            if (BrooklynObject.class.isAssignableFrom(c)) {
                @SuppressWarnings("unchecked")
                Class<? extends BrooklynObject> brooklynClass = (Class<? extends BrooklynObject>) c;
                BrooklynDynamicType<?, ?> dynamicType = BrooklynTypes.getDefinedBrooklynType(brooklynClass);
                BrooklynType type = dynamicType.getSnapshot();
                for (ConfigKey<?> x: type.getConfigKeys()) {
                    WeightedParameter fieldConfig = getFieldConfig(x, dynamicType.getConfigKeyField(x.getName()));
                    parameters.appendIfNotNull(fieldConfig);
                }
                Collections.sort(parameters, new WeightedParameterComparator());
                return FluentIterable.from(parameters)
                        .transform(new SpecParameterTransformer()).toList();
            } else {
                return ImmutableList.of();
            }
        }

        public static WeightedParameter getFieldConfig(ConfigKey<?> config, Field configKeyField) {
            if (configKeyField == null) return null;
            CatalogConfig catalogConfig = configKeyField.getAnnotation(CatalogConfig.class);
            String label = config.getName();
            Double priority = null;
            if (catalogConfig != null) {
                label = Maybe.fromNullable(catalogConfig.label()).or(config.getName());
                priority = catalogConfig.priority();
            }
            @SuppressWarnings({ "unchecked", "rawtypes" })
            SpecParameter<?> param = new BasicSpecParameter(label, priority != null, config);
            return new WeightedParameter(priority, param);
        }

        private static <T extends Entity> Class<? extends T> getEntityImplementedBy(ManagementContext mgmt, Class<T> type) {
            return mgmt.getEntityManager().getEntityTypeRegistry().getImplementedBy(type);
        }
    }


}
