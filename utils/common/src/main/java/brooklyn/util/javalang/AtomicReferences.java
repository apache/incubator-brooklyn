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
package brooklyn.util.javalang;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

public class AtomicReferences {

    /** sets the atomic reference to the given value, and returns whether there is any change */
    public static boolean setIfDifferent(AtomicBoolean ref, boolean value) {
        return ref.getAndSet(value) != value;
    }

    /** sets the atomic reference to the given value, and returns whether there is any change */
    public static <T> boolean setIfDifferent(AtomicReference<T> ref, T value) {
        return !Objects.equal(ref.getAndSet(value), value);
    }
    
    /** returns the given atomic as a Supplier */
    public static <T> Supplier<T> supplier(final AtomicReference<T> ref) {
        Preconditions.checkNotNull(ref);
        return new Supplier<T>() {
            @Override public T get() { return ref.get(); }
            @Override public String toString() { return "AtomicRefSupplier"; }
        };
    }
}
