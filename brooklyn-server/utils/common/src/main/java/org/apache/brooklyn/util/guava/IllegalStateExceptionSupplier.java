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
package org.apache.brooklyn.util.guava;

import org.apache.brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

public class IllegalStateExceptionSupplier implements Supplier<RuntimeException> {

    public static final IllegalStateExceptionSupplier EMPTY_EXCEPTION = new IllegalStateExceptionSupplier();
    
    protected final Supplier<String> message;
    protected final Throwable cause;
    
    public IllegalStateExceptionSupplier() { this((Supplier<String>)null, null); }
    public IllegalStateExceptionSupplier(String message) { this(message, null); }
    public IllegalStateExceptionSupplier(Throwable cause) { this(new CollapseTextSupplier(cause), cause); }
    public IllegalStateExceptionSupplier(String message, Throwable cause) { this(Suppliers.ofInstance(message), cause); } 
    public IllegalStateExceptionSupplier(Supplier<String> message, Throwable cause) { 
        this.message = message;
        this.cause = cause;
    }

    private static class CollapseTextSupplier implements Supplier<String> {
        final Throwable cause;
        CollapseTextSupplier(Throwable cause) { this.cause = cause; }
        @Override
        public String get() {
            return Exceptions.collapseText(cause);
        }
    }
    @Override
    public RuntimeException get() {
        return new IllegalStateException(message==null ? null : message.get(), cause);
    }

}
