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

import java.util.List;

import brooklyn.entity.Effector;
import brooklyn.entity.ParameterType;
import brooklyn.entity.effector.EffectorTasks.EffectorTaskFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;

@Beta // added in 0.6.0
public class EffectorAndBody<T> extends EffectorBase<T> implements EffectorWithBody<T> {

    private static final long serialVersionUID = -6023389678748222968L;
    private final EffectorTaskFactory<T> body;

    public EffectorAndBody(Effector<T> original, EffectorTaskFactory<T> body) {
        this(original.getName(), original.getReturnType(), original.getParameters(), original.getDescription(), body);
    }
    
    public EffectorAndBody(String name, Class<T> returnType, List<ParameterType<?>> parameters, String description, EffectorTaskFactory<T> body) {
        super(name, returnType, parameters, description);
        this.body = body;
    }

    @Override
    public EffectorTaskFactory<T> getBody() {
        return body;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), getBody());
    }
    
    @Override
    public boolean equals(Object other) {
        return super.equals(other) && Objects.equal(getBody(), ((EffectorAndBody<?>)other).getBody());
    }

}
