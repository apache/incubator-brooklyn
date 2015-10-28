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
package org.apache.brooklyn.core.catalog.internal;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogInput;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityType;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.entity.EntityDynamicType;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.objs.BrooklynTypes;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.StringPredicates;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

public class CatalogInputDto<T> implements CatalogInput<T> {
    private static final String DEFAULT_TYPE = "string";
    private static final Map<String, Class<?>> BUILT_IN_TYPES = ImmutableMap.<String, Class<?>>builder()
            .put(DEFAULT_TYPE, String.class)
            .put("integer", Integer.class)
            .put("long", Long.class)
            .put("float", Float.class)
            .put("double", Double.class)
            .put("timestamp", Date.class)
            .build();

    private static final Map<String, Predicate<?>> BUILT_IN_CONSTRAINTS = ImmutableMap.<String, Predicate<?>>of(
            "required", StringPredicates.isNonBlank());

    private String label;
    private boolean pinned;
    private ConfigKey<T> type;

    public CatalogInputDto(String label, boolean pinned, ConfigKey<T> type) {
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

    public static final class ParseYamlInputs {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public static List<CatalogInput<?>> parseInputs(List<?> inputsRaw, BrooklynClassLoadingContext loader) {
            List<CatalogInput<?>> inputs = new ArrayList<>(inputsRaw.size());
            for (Object obj : inputsRaw) {
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
                inputs.add(new CatalogInputDto(Maybe.fromNullable(label).or(name), true, inputType));
            }
            return inputs;
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

    public static final class ParseClassInputs {
        private static final class WeightedCatalogInput {
            private Double weight;
            private CatalogInput<?> input;
            public WeightedCatalogInput(Double weight, CatalogInput<?> input) {
                this.weight = weight;
                this.input = input;
            }
            public Double getWeight() {return weight; }
            public CatalogInput<?> getInput() { return input; }
        }
        private static final class InputsComparator implements Comparator<WeightedCatalogInput> {
            @Override
            public int compare(WeightedCatalogInput o1, WeightedCatalogInput o2) {
                if (o1.getWeight() == o2.getWeight()) {
                    return 0;
                } else if (o1.getWeight() == null) {
                    return 1;
                } else if (o2.getWeight() == null) {
                    return -1;
                } else {
                    return Double.compare(o1.getWeight(),  o2.getWeight());
                }
            }
        }
        private static final class InputsTransformer implements Function<WeightedCatalogInput, CatalogInput<?>> {
            @Override
            public CatalogInput<?> apply(WeightedCatalogInput input) {
                return input.getInput();
            }
        }

        public static List<CatalogInput<?>> parseInputs(Class<?> c) {
            MutableList<WeightedCatalogInput> inputs = MutableList.<WeightedCatalogInput>of();
            if (Entity.class.isAssignableFrom(c)) {
                @SuppressWarnings("unchecked")
                Class<? extends Entity> entityClass = (Class<? extends Entity>) c;
                EntityDynamicType dynamicType = BrooklynTypes.getDefinedEntityType(entityClass);
                EntityType type = dynamicType.getSnapshot();
                for (ConfigKey<?> x: type.getConfigKeys()) {
                    WeightedCatalogInput fieldConfig = getFieldConfig(x, dynamicType.getConfigKeyField(x.getName()));
                    inputs.appendIfNotNull(fieldConfig);
                }
                Collections.sort(inputs, new InputsComparator());
                return FluentIterable.from(inputs)
                        .transform(new InputsTransformer()).toList();
            } else {
                return ImmutableList.<CatalogInput<?>>of();
            }
        }

        public static WeightedCatalogInput getFieldConfig(ConfigKey<?> config, Field configKeyField) {
            if (configKeyField == null) return null;
            CatalogConfig catalogConfig = configKeyField.getAnnotation(CatalogConfig.class);
            String label = config.getName();
            Double priority = null;
            if (catalogConfig != null) {
                label = Maybe.fromNullable(catalogConfig.label()).or(config.getName());
                priority = catalogConfig.priority();
            }
            @SuppressWarnings({ "unchecked", "rawtypes" })
            CatalogInput<?> input = new CatalogInputDto(label, priority != null, config);
            return new WeightedCatalogInput(priority, input);
        }
    }

}
