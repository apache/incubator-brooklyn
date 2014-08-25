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
package brooklyn.util.exceptions;

import javax.annotation.Nullable;

import com.google.common.annotations.Beta;
import com.google.common.base.Supplier;

/** A reference to an object which can carry an object alongside it. */
@Beta
public class ReferenceWithError<T> implements Supplier<T> {

    private final T object;
    private final Throwable error;
    private final boolean maskError;

    /** returns a reference which includes an error, and where attempts to get the content cause the error to throw */
    public static <T> ReferenceWithError<T> newInstanceThrowingError(T object, Throwable error) {
        return new ReferenceWithError<T>(object, error, false);
    }
    
    /** returns a reference which includes an error, but attempts to get the content do not cause the error to throw */
    public static <T> ReferenceWithError<T> newInstanceMaskingError(T object, Throwable error) {
        return new ReferenceWithError<T>(object, error, true);
    }
    
    /** returns a reference which includes an error, but attempts to get the content do not cause the error to throw */
    public static <T> ReferenceWithError<T> newInstanceWithoutError(T object) {
        return new ReferenceWithError<T>(object, null, false);
    }
    
    protected ReferenceWithError(@Nullable T object, @Nullable Throwable error, boolean maskError) {
        this.object = object;
        this.error = error;
        this.maskError = maskError;
    }

    /** whether this will mask any error on an attempt to {@link #get()};
     * if false (if created with {@link #newInstanceThrowingError(Object, Throwable)}) a call to {@link #get()} will throw if there is an error;
     * true if created with {@link #newInstanceMaskingError(Object, Throwable)} and {@link #get()} will not throw */
    public boolean masksErrorIfPresent() {
        return maskError;
    }

    /** returns the underlying value, throwing if there is an error which is not masked (ie {@link #throwsErrorOnAccess()} is set) */
    public T get() {
        if (masksErrorIfPresent()) {
            return getWithoutError();
        }
        return getWithError();
    }

    /** returns the object, ignoring any error (even non-masked) */
    public T getWithoutError() {
        return object;
    }

    /** throws error, even if there is one (even if masked), else returns the object */
    public T getWithError() {
        checkNoError();
        return object;
    }

    /** throws if there is an error (even if masked) */
    public void checkNoError() {
        if (hasError())
            Exceptions.propagate(error);
    }

    /** returns the error (not throwing) */
    public Throwable getError() {
        return error;
    }
    
    /** true if there is an error (whether masked or not) */
    public boolean hasError() {
        return error!=null;
    }
    
}
