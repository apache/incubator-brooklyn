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
package brooklyn.management.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.ExecutionManager;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.task.SingleThreadedScheduler;

public class AsyncCollectionChangeAdapter<Item> implements CollectionChangeListener<Item> {
	
    private static final Logger LOG = LoggerFactory.getLogger(AsyncCollectionChangeAdapter.class);

    private final ExecutionManager executor;
    private final CollectionChangeListener<Item> delegate;
    
    public AsyncCollectionChangeAdapter(ExecutionManager executor, CollectionChangeListener<Item> delegate) {
    	this.executor = checkNotNull(executor, "executor");
        this.delegate = checkNotNull(delegate, "delegate");
        ((BasicExecutionManager) executor).setTaskSchedulerForTag(delegate, SingleThreadedScheduler.class);
    }

    @Override
    public void onItemAdded(final Item item) {
    	executor.submit(MutableMap.of("tag", delegate), new Runnable() {
    		public void run() {
    			try {
    				delegate.onItemAdded(item);
    			} catch (Throwable t) {
    				LOG.warn("Error notifying listener of itemAdded("+item+")", t);
    				Exceptions.propagate(t);
    			}
    		}
    	});
    }
    
    @Override
    public void onItemRemoved(final Item item) {
    	executor.submit(MutableMap.of("tag", delegate), new Runnable() {
    		public void run() {
    			try {
    				delegate.onItemRemoved(item);
    			} catch (Throwable t) {
    				LOG.warn("Error notifying listener of itemAdded("+item+")", t);
    				Exceptions.propagate(t);
    			}
    		}
    	});
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof AsyncCollectionChangeAdapter) && 
        		delegate.equals(((AsyncCollectionChangeAdapter<?>) other).delegate);
    }
}
