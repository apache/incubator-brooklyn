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

import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry.RegisteredTypeKind;

/** Visitor adapter which can be used to ensure all kinds are supported
 * <p>
 * By design this class may have abstract methods added without notification,
 * and subclasses will be responsible for providing the implementation in order to ensure compatibility. */
public abstract class RegisteredTypeKindVisitor<T> {
    
    public T visit(RegisteredTypeKind kind) {
        if (kind==null) return visitNull();
        switch (kind) {
        case SPEC: return visitSpec();
        case BEAN: return visitBean();
        default:
            throw new IllegalStateException("Unexpected registered type kind: "+kind);
        }
    }

    protected T visitNull() {
        throw new NullPointerException("Registered type kind must not be null");
    }

    protected abstract T visitSpec();
    protected abstract T visitBean();
}
