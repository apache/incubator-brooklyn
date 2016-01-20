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
package org.apache.brooklyn.util.core.task;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import org.apache.brooklyn.util.core.task.TaskInternal.TaskCancellationMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ExecutionList;
import com.google.common.util.concurrent.ForwardingFuture.SimpleForwardingFuture;
import com.google.common.util.concurrent.ListenableFuture;

/** Wraps a Future, making it a ListenableForwardingFuture, but with the caller having the responsibility to:
 * <li> invoke the listeners on job completion (success or error)
 * <li> invoke the listeners on cancel
 * 
 * @deprecated since 0.9.0 likely to leave the public API */
@Deprecated  // TODO just one subclass, it can hold the behaviour we need from this, 
// and the methods here are surprising as they expect the caller to notify the list
public abstract class ListenableForwardingFuture<T> extends SimpleForwardingFuture<T> implements ListenableFuture<T> {

    private static final Logger log = LoggerFactory.getLogger(ListenableForwardingFuture.class);
    
    // TODO these are never accessed or used
    final ExecutionList listeners;
    
    protected ListenableForwardingFuture(Future<T> delegate) {
        super(delegate);
        this.listeners = new ExecutionList();
    }

    protected ListenableForwardingFuture(Future<T> delegate, ExecutionList list) {
        super(delegate);
        this.listeners = list;
    }

    private static boolean warned = false;
    
    @Override
    public void addListener(Runnable listener, Executor executor) {
        if (!warned) {
            log.warn("Use of deprecated ListenableForwardingFuture.addListener at "+this+" (future calls will not be logged)", new Throwable("stack trace"));
            warned = true;
        }
        
        listeners.add(listener, executor);
    }
    
    public abstract boolean cancel(TaskCancellationMode mode);
    
    public final boolean cancel(boolean mayInterrupt) {
        return cancel(TaskCancellationMode.INTERRUPT_TASK_AND_DEPENDENT_SUBMITTED_TASKS);
    }
    
}
