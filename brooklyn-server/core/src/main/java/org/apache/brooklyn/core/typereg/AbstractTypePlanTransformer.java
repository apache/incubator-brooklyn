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

import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredTypeLoadingContext;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience supertype for {@link BrooklynTypePlanTransformer} instances.
 * <p>
 * This supplies a default {@link #scoreForType(RegisteredType, RegisteredTypeLoadingContext)}
 * method which returns 1 if the format code matches,
 * and otherwise branches to two methods {@link #scoreForNullFormat(Object, RegisteredType, RegisteredTypeLoadingContext)}
 * and {@link #scoreForNonmatchingNonnullFormat(String, Object, RegisteredType, RegisteredTypeLoadingContext)}
 * which subclasses can implement.  (Often the implementation of the latter is 0.)
 */
public abstract class AbstractTypePlanTransformer implements BrooklynTypePlanTransformer {

    private static final Logger log = LoggerFactory.getLogger(AbstractTypePlanTransformer.class);
    
    protected ManagementContext mgmt;

    @Override
    public void setManagementContext(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

    private final String format;
    private final String formatName;
    private final String formatDescription;
    
    protected AbstractTypePlanTransformer(String format, String formatName, String formatDescription) {
        this.format = format;
        this.formatName = formatName;
        this.formatDescription = formatDescription;
    }
    
    @Override
    public String getFormatCode() {
        return format;
    }

    @Override
    public String getFormatName() {
        return formatName;
    }

    @Override
    public String getFormatDescription() {
        return formatDescription;
    }

    @Override
    public String toString() {
        return getFormatCode()+":"+JavaClassNames.simpleClassName(this);
    }
    
    @Override
    public double scoreForType(RegisteredType type, RegisteredTypeLoadingContext context) {
        if (getFormatCode().equals(type.getPlan().getPlanFormat())) return 1;
        if (type.getPlan().getPlanFormat()==null)
            return scoreForNullFormat(type.getPlan().getPlanData(), type, context);
        else
            return scoreForNonmatchingNonnullFormat(type.getPlan().getPlanFormat(), type.getPlan().getPlanData(), type, context);
    }

    protected abstract double scoreForNullFormat(Object planData, RegisteredType type, RegisteredTypeLoadingContext context);
    protected abstract double scoreForNonmatchingNonnullFormat(String planFormat, Object planData, RegisteredType type, RegisteredTypeLoadingContext context);

    /** delegates to more specific abstract create methods,
     * and performs common validation and customisation of the items created.
     * <p>
     * this includes:
     * <li> setting the {@link AbstractBrooklynObjectSpec#catalogItemId(String)}
     */
    @Override
    public Object create(final RegisteredType type, final RegisteredTypeLoadingContext context) {
        try {
            return tryValidate(new RegisteredTypeKindVisitor<Object>() {
                @Override protected Object visitSpec() {
                    try { 
                        AbstractBrooklynObjectSpec<?, ?> result = createSpec(type, context);
                        // see notes on catalogItemIdIfNotNull
                        result.catalogItemIdIfNotNull(type.getId());
                        return result;
                    } catch (Exception e) { throw Exceptions.propagate(e); }
                }
                @Override protected Object visitBean() {
                    try { 
                        return createBean(type, context);
                    } catch (Exception e) { throw Exceptions.propagate(e); }
                }
                
            }.visit(type.getKind()), type, context).get();
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            if (!(e instanceof UnsupportedTypePlanException)) {
                log.debug("Could not instantiate "+type+" (rethrowing): "+Exceptions.collapseText(e));
            }
            throw Exceptions.propagate(e);
        }
    }
    
    /** Validates the object. Subclasses may do further validation based on the context. 
     * @throw UnsupportedTypePlanException if we want to quietly abandon this, any other exception to report the problem, when validation fails
     * @return the created object for fluent usage */
    protected <T> Maybe<T> tryValidate(T createdObject, RegisteredType type, RegisteredTypeLoadingContext constraint) {
        return RegisteredTypes.tryValidate(createdObject, type, constraint);
    }

    protected abstract AbstractBrooklynObjectSpec<?,?> createSpec(RegisteredType type, RegisteredTypeLoadingContext context) throws Exception;

    protected abstract Object createBean(RegisteredType type, RegisteredTypeLoadingContext context) throws Exception;
    
}
