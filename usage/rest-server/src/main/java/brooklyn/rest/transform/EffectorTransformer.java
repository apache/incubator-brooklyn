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
package brooklyn.rest.transform;

import java.net.URI;
import java.util.Set;

import javax.annotation.Nullable;

import brooklyn.entity.Effector;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.rest.domain.EffectorSummary;
import brooklyn.rest.domain.EffectorSummary.ParameterSummary;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class EffectorTransformer {

    public static EffectorSummary effectorSummary(EntityLocal entity, Effector<?> effector) {
        String applicationUri = "/v1/applications/" + entity.getApplicationId();
        String entityUri = applicationUri + "/entities/" + entity.getId();
        return new EffectorSummary(effector.getName(), effector.getReturnTypeName(),
                 ImmutableSet.copyOf(Iterables.transform(effector.getParameters(),
                new Function<ParameterType<?>, EffectorSummary.ParameterSummary<?>>() {
                    @Override
                    public EffectorSummary.ParameterSummary<?> apply(@Nullable ParameterType<?> parameterType) {
                        return parameterSummary(parameterType);
                    }
                })), effector.getDescription(), ImmutableMap.of(
                "self", URI.create(entityUri + "/effectors/" + effector.getName()),
                "entity", URI.create(entityUri),
                "application", URI.create(applicationUri)
        ));
    }

    public static EffectorSummary effectorSummaryForCatalog(Effector<?> effector) {
        Set<EffectorSummary.ParameterSummary<?>> parameters = ImmutableSet.copyOf(Iterables.transform(effector.getParameters(),
                new Function<ParameterType<?>, EffectorSummary.ParameterSummary<?>>() {
                    @Override
                    public EffectorSummary.ParameterSummary<?> apply(ParameterType<?> parameterType) {
                        return parameterSummary(parameterType);
                    }
                }));
        return new EffectorSummary(effector.getName(),
                effector.getReturnTypeName(), parameters, effector.getDescription(), null);
    }
    
    @SuppressWarnings("unchecked")
    protected static EffectorSummary.ParameterSummary<?> parameterSummary(ParameterType<?> parameterType) {
        return new ParameterSummary(parameterType.getName(), parameterType.getParameterClassName(), 
                parameterType.getDescription(), parameterType.getDefaultValue());
    }
}
