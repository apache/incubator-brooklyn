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
package brooklyn.entity.basic;

import brooklyn.entity.Entity;
import groovy.lang.Closure;

import java.util.HashMap;
import java.util.Map;

public class ClosureEntityFactory<T extends Entity> extends AbstractConfigurableEntityFactory<T> {
    private final Closure<T> closure;

    public ClosureEntityFactory(Closure<T> closure){
        this(new HashMap(),closure);
    }

    public ClosureEntityFactory(Map flags, Closure<T> closure) {
        super(flags);
        this.closure = closure;
    }

    public T newEntity2(Map flags, Entity parent) {
        if (closure.getMaximumNumberOfParameters()>1)
            return closure.call(flags, parent);
        else {
            //leaving out the parent is discouraged
            T entity = closure.call(flags);
            if(parent!=null && entity.getParent()==null){
                entity.setParent(parent);
            }

            return entity;
        }
    }
}