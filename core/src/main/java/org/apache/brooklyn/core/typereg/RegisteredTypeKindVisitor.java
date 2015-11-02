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

import org.apache.brooklyn.api.typereg.RegisteredType;

/** Visitor adapter which can be used to ensure all kinds are supported
 * <p>
 * By design this class may have abstract methods added without notification,
 * and subclasses will be responsible for providing the implementation in order to ensure compatibility. */
public abstract class RegisteredTypeKindVisitor<T> {
    
    public T visit(RegisteredType type) {
        if (type==null) throw new NullPointerException("Registered type must not be null");
        switch (type.getKind()) {
        case SPEC: return visitSpec(type);
        case BEAN: return visitBean(type);
        // others go here
        default:
            throw new IllegalStateException("Unexpected registered type: "+type.getClass());
        }
    }

    protected abstract T visitSpec(RegisteredType type);
    protected abstract T visitBean(RegisteredType type);
}
