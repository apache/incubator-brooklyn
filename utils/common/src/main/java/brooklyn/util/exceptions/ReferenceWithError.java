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

import com.google.common.base.Supplier;

/** A reference to an object which can carry an object alongside it. */
public class ReferenceWithError<T> implements Supplier<T> {

    private final T object;
    private final Throwable error;
    private final boolean throwErrorOnAccess;

    /** returns a reference which includes an error, and where attempts to get the content cause the error to throw */
    public static <T> ReferenceWithError<T> newInstanceWithFatalError(T object, Throwable error) {
        return new ReferenceWithError<T>(object, error, true);
    }
    
    /** returns a reference which includes an error, but attempts to get the content do not cause the error to throw */
    public static <T> ReferenceWithError<T> newInstanceWithInformativeError(T object, Throwable error) {
        return new ReferenceWithError<T>(object, error, false);
    }
    
    /** returns a reference which includes an error, but attempts to get the content do not cause the error to throw */
    public static <T> ReferenceWithError<T> newInstanceWithNoError(T object) {
        return new ReferenceWithError<T>(object, null, false);
    }
    
    protected ReferenceWithError(@Nullable T object, @Nullable Throwable error, boolean throwErrorOnAccess) {
        this.object = object;
        this.error = error;
        this.throwErrorOnAccess = throwErrorOnAccess;
    }

    public boolean throwsErrorOnAccess() {
        return throwErrorOnAccess;
    }

    public T get() {
        if (throwsErrorOnAccess()) {
            return getOrThrowError();
        }
        return getIgnoringError();
    }

    public T getIgnoringError() {
        return object;
    }

    public T getOrThrowError() {
        checkNoError();
        return object;
    }

    public void checkNoError() {
        if (hasError())
            Exceptions.propagate(error);
    }
    
    public Throwable getError() {
        return error;
    }
    
    public boolean hasError() {
        return error!=null;
    }
    
}
