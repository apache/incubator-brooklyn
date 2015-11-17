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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredTypeLoadingContext;
import org.apache.brooklyn.core.typereg.AbstractTypePlanTransformer;
import org.apache.brooklyn.core.typereg.JavaClassNameTypePlanTransformer;
import org.apache.brooklyn.core.typereg.TypePlanTransformers;
import org.apache.brooklyn.util.text.Identifiers;

/**
 * Allows a caller to register a spec (statically) and get a UID for it --
 * <pre> {@code
 * String specId = StaticTypePlanTransformer.registerSpec(EntitySpec.create(BasicEntity.class));
 * }</pre>
 * and then build a plan referring to that type name, such as:
 * <pre> {@code
 *  brooklyn.catalog:
 *    id: test.inputs
 *    version: 0.0.1
 *    item: <specId>
 * } </pre>
 * <p>
 * For use when testing type plan resolution. 
 * <p>
 * This is different to {@link JavaClassNameTypePlanTransformer} as that one
 * does a <code>Class.forName(typeName)</code> to create specs, and this one uses a static registry.
 * <p>
 * Use {@link #forceInstall()} to set up and {@link #clearForced()} after use (in a finally or "AfterTest" block)
 * to prevent interference with other tests.
 */
public class StaticTypePlanTransformer extends AbstractTypePlanTransformer {
    
    public StaticTypePlanTransformer() {
        super("static-types", "Static Type", "Static transformer for use in tests");
    }

    private static final Map<String, AbstractBrooklynObjectSpec<?, ?>> REGISTERED_SPECS = new ConcurrentHashMap<>();

    public static void forceInstall() {
        TypePlanTransformers.forceAvailable(StaticTypePlanTransformer.class, JavaClassNameTypePlanTransformer.class);
    }
    
    public static void clearForced() {
        TypePlanTransformers.clearForced();
        REGISTERED_SPECS.clear();
    }
    
    public static String registerSpec(AbstractBrooklynObjectSpec<?, ?> spec) {
        String id = Identifiers.makeRandomId(10);
        REGISTERED_SPECS.put(id, spec);
        return id;
    }

    @Override
    public double scoreForTypeDefinition(String formatCode, Object catalogData) {
        // not supported
        return 0;
    }

    @Override
    public List<RegisteredType> createFromTypeDefinition(String formatCode, Object catalogData) {
        // not supported
        return null;
    }

    @Override
    protected double scoreForNullFormat(Object planData, RegisteredType type, RegisteredTypeLoadingContext context) {
        if (REGISTERED_SPECS.containsKey(type.getId())) return 1;
        if (REGISTERED_SPECS.containsKey(planData)) return 1;
        return 0;
    }

    @Override
    protected double scoreForNonmatchingNonnullFormat(String planFormat, Object planData, RegisteredType type, RegisteredTypeLoadingContext context) {
        // not supported
        return 0;
    }

    @Override
    protected AbstractBrooklynObjectSpec<?, ?> createSpec(RegisteredType type, RegisteredTypeLoadingContext context) throws Exception {
        if (REGISTERED_SPECS.containsKey(type.getId()))
            return get(type.getId());
        if (REGISTERED_SPECS.containsKey(type.getPlan().getPlanData()))
            return get((String)type.getPlan().getPlanData());
        return null;
    }

    @Override
    protected Object createBean(RegisteredType type, RegisteredTypeLoadingContext context) throws Exception {
        // not supported
        return null;
    }

    public static AbstractBrooklynObjectSpec<?, ?> get(String typeName) {
        return REGISTERED_SPECS.get(typeName);
    }


}
