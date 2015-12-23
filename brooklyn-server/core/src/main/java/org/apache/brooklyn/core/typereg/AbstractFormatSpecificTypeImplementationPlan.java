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

import org.apache.brooklyn.api.typereg.RegisteredType.TypeImplementationPlan;

/** Abstract superclass for plans to create {@link TypeImplementationPlan} with strong types on 
 * {@link #getPlanData()} and ensuring the correct format (or null for no format) */
public abstract class AbstractFormatSpecificTypeImplementationPlan<T> extends BasicTypeImplementationPlan {
    
    public AbstractFormatSpecificTypeImplementationPlan(String format, T data) {
        super(format, data);
    }
    public AbstractFormatSpecificTypeImplementationPlan(String expectedFormat, Class<T> expectedDataType, TypeImplementationPlan otherPlan) {
        super(expectedFormat!=null ? expectedFormat : otherPlan.getPlanFormat(), otherPlan.getPlanData());
        if (!expectedDataType.isInstance(otherPlan.getPlanData())) {
            throw new IllegalArgumentException("Plan "+otherPlan+" does not have "+expectedDataType+" data so cannot cast to "+this);
        }
        if (expectedFormat!=null && otherPlan.getPlanFormat()!=null) {
            if (!otherPlan.getPlanFormat().equals(expectedFormat)) {
                throw new IllegalArgumentException("Plan "+otherPlan+" in wrong format "+otherPlan.getPlanFormat()+", when expecting "+expectedFormat);
            }
        }
    }
    
    @Override
    public String getPlanFormat() {
        return format;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getPlanData() {
        return (T)super.getPlanData();
    }
}
