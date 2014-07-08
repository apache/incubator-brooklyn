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
package brooklyn.entity.effector;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.ParameterType;
import brooklyn.entity.effector.EffectorTasks.EffectorTaskFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;

/** concrete implementation of Effector interface, 
 * but not (at this level of the hirarchy) defining an implementation 
 * (see {@link EffectorTaskFactory} and {@link EffectorWithBody}) */
public class EffectorBase<T> implements Effector<T> {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(EffectorBase.class);
    
    private static final long serialVersionUID = -4153962199078384835L;
    
    private final String name;
    private final Class<T> returnType;
    private final List<ParameterType<?>> parameters;
    private final String description;

    public EffectorBase(String name, Class<T> returnType, List<ParameterType<?>> parameters, String description) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = new ArrayList<ParameterType<?>>(parameters);
        this.description = description;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Class<T> getReturnType() {
        return returnType;
    }

    @Override
    public String getReturnTypeName() {
        return returnType.getCanonicalName();
    }

    @Override
    public List<ParameterType<?>> getParameters() {
        return parameters;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        List<String> parameterNames = new ArrayList<String>(parameters.size());
        for (ParameterType<?> parameter: parameters) {
            String parameterName = (parameter.getName() != null) ? parameter.getName() : "<unknown>";
            parameterNames.add(parameterName);
        }
        return name+"["+Joiner.on(",").join(parameterNames)+"]";
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, returnType, parameters, description);
    }
    
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof EffectorBase)) return false;
        if (!(other.getClass().equals(getClass()))) return false;
        if (!Objects.equal(hashCode(), other.hashCode())) return false;
        return Objects.equal(getName(), ((EffectorBase<?>)other).getName()) &&
            Objects.equal(getReturnType(), ((EffectorBase<?>)other).getReturnType()) &&
            Objects.equal(getParameters(), ((EffectorBase<?>)other).getParameters()) &&
            Objects.equal(getDescription(), ((EffectorBase<?>)other).getDescription());
    }
    
}
