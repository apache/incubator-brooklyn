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
package brooklyn.util.task;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import com.google.common.util.concurrent.ExecutionList;
import com.google.common.util.concurrent.ForwardingFuture.SimpleForwardingFuture;
import com.google.common.util.concurrent.ListenableFuture;

/** Wraps a Future, making it a ListenableForwardingFuture, but with the caller having the resposibility to:
 * <li> invoke the listeners on job completion (success or error)
 * <li> invoke the listeners on cancel */
public abstract class ListenableForwardingFuture<T> extends SimpleForwardingFuture<T> implements ListenableFuture<T> {

    final ExecutionList listeners;
    
    protected ListenableForwardingFuture(Future<T> delegate) {
        super(delegate);
        this.listeners = new ExecutionList();
    }

    protected ListenableForwardingFuture(Future<T> delegate, ExecutionList list) {
        super(delegate);
        this.listeners = list;
    }

    @Override
    public void addListener(Runnable listener, Executor executor) {
        listeners.add(listener, executor);
    }
    
}
