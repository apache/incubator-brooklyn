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
package brooklyn.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.google.common.annotations.Beta;

/** Wraps a Runnable as a Callable. Like {@link Executors#callable(Runnable, Object)} but including the underlying toString. */
@Beta
public class CallableFromRunnable<T> implements Callable<T> {
    
    public static <T> CallableFromRunnable<T> newInstance(Runnable task, T result) {
        return new CallableFromRunnable<T>(task, result);
    }
    
    private final Runnable task;
    private final T result;
    
    protected CallableFromRunnable(Runnable task, T result) {
        this.task = task;
        this.result = result;
    }
    
    public T call() {
        task.run();
        return result;
    }
    
    @Override
    public String toString() {
        if (result!=null)
            return "CallableFromRunnable["+task+(result!=null ? "->"+result : "")+"]";
        else
            return ""+task;
    }
}