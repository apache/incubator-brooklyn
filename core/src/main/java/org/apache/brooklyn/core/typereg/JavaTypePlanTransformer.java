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
package org.apache.brooklyn.core.typereg;

import java.util.List;

import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredTypeConstraint;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.util.text.Identifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instantiates classes from a registered type which simply
 * defines the java class name and OSGi bundles to use.
 * <p>
 * This is used where a {@link RegisteredType} is defined simply with the name of a java class
 * e.g. with a no-arg constructor -- no YAML etc just the name of the class.
 */
public class JavaTypePlanTransformer extends AbstractTypePlanTransformer {
    
    private static final Logger log = LoggerFactory.getLogger(JavaTypePlanTransformer.class);
    public static final String FORMAT = "java-type-name";

    public static class JavaTypeNameImplementation extends AbstractCustomImplementationPlan<String> {
        private transient Class<?> cachedType;
        public JavaTypeNameImplementation(String javaType) {
            super(FORMAT, javaType);
        }
        public Class<?> getCachedType() {
            return cachedType;
        }
    }

    public JavaTypePlanTransformer() {
        super(FORMAT, "Java type name", "Expects a java type name in a format suitable for use with ClassLoader.loadClass");
    }

    @Override
    protected double scoreForNullFormat(Object planData, RegisteredType type, RegisteredTypeConstraint context) {
        if (type.getPlan().getPlanData() instanceof String && 
                ((String)type.getPlan().getPlanData()).matches(Identifiers.JAVA_BINARY_REGEX)) {
            return 0.1;
        }
        return 0;
    }
    
    @Override
    protected double scoreForNonmatchingNonnullFormat(String planFormat, Object planData, RegisteredType type, RegisteredTypeConstraint context) {
        return 0;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    protected AbstractBrooklynObjectSpec<?,?> createSpec(RegisteredType type, RegisteredTypeConstraint context) throws Exception {
        Class targetType = getType(type, context);
        Class specType = RegisteredTypeConstraints.spec((Class)targetType).getJavaSuperType();
        AbstractBrooklynObjectSpec result = (AbstractBrooklynObjectSpec) specType.getConstructor(Class.class).newInstance(targetType);
        return result;
    }

    @Override
    protected Object createBean(RegisteredType type, RegisteredTypeConstraint context) throws Exception {
        return getType(type, context).newInstance();
    }

    private Class<?> getType(RegisteredType type, RegisteredTypeConstraint context) {
        if (type.getPlan() instanceof JavaTypeNameImplementation) {
            Class<?> cachedType = ((JavaTypeNameImplementation)type.getPlan()).getCachedType();
            if (cachedType==null) {
                log.debug("Storing cached type "+cachedType+" for "+type);
                cachedType = loadType(type, context);
            }
            return cachedType;
        }
        return loadType(type, context);
    }
    private Class<?> loadType(RegisteredType type, RegisteredTypeConstraint context) {
        return CatalogUtils.newClassLoadingContext(mgmt, type).loadClass( ((String)type.getPlan().getPlanData()) );
    }

    
    // TODO not supported as a catalog format (yet)
    @Override
    public double scoreForTypeDefinition(String formatCode, Object catalogData) {
        return 0;
    }

    @Override
    public List<RegisteredType> createFromTypeDefinition(String formatCode, Object catalogData) {
        throw new UnsupportedTypePlanException("this transformer does not support YAML catalog additions");
    }

}
