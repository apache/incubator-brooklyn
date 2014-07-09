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
package brooklyn.util.mutex;

import java.util.List;
import java.util.Set;

import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Time;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;


/** A subclass of {@link SemaphoreWithOwners} 
 * which additionally sets Task blocking information. 
 * <p>
 * TODO As tasks are distributed this should support distribution across the management context. */
public class SemaphoreForTasks extends SemaphoreWithOwners {
    
    private static final long serialVersionUID = 7898283056223005952L;
    
    /** unused at present, but wanted on the API for when this may be federated */
    @SuppressWarnings("unused")
    private final ManagementContext mgmt;
    
    final private MutableList<Task<?>> owningTasks = new MutableList<Task<?>>();
    final private MutableSet<Task<?>> requestingTasks = new MutableSet<Task<?>>();

    public SemaphoreForTasks(String name, ManagementContext mgmt) {
        super(name);
        this.mgmt = Preconditions.checkNotNull(mgmt);
    }
    
    public SemaphoreForTasks(String name, int permits, boolean fair, ManagementContext mgmt) {
        super(name, permits, fair);
        this.mgmt = Preconditions.checkNotNull(mgmt);
    }
    
    public synchronized Set<Task<?>> getRequestingTasks() {
        return ImmutableSet.copyOf(requestingTasks);
    }
    
    public synchronized List<Task<?>> getOwningTasks() {
        return ImmutableList.copyOf(owningTasks);
    }

    @Override
    protected synchronized void onRequesting() {
        if (!owningTasks.isEmpty() || !requestingTasks.isEmpty()) {
            Tasks.setBlockingTask( !requestingTasks.isEmpty() ? Iterables.getLast(requestingTasks) : Iterables.getFirst(owningTasks, null) );
            Tasks.setBlockingDetails("Waiting on semaphore "+getName()+" ("+getDescription()+"); "
                + "queued at "+Time.makeDateString()+" when "+getRequestingThreads().size()+" ahead in queue");
        }
        requestingTasks.addIfNotNull(Tasks.current());
        super.onRequesting();
    }
    
    @Override
    protected synchronized void onRequestFinished() {
        super.onRequestFinished();
        requestingTasks.removeIfNotNull(Tasks.current());
        
        Tasks.resetBlockingDetails();
        Tasks.resetBlockingTask();
    }
    
    @Override
    protected synchronized void onAcquired(int permits) {
        super.onAcquired(permits);
        for (int i=0; i<permits; i++)
            owningTasks.appendIfNotNull(Tasks.current());
    }
    
    @Override
    protected synchronized void onReleased(int permits) {
        super.onReleased(permits);
        for (int i=0; i<permits; i++)
            owningTasks.removeIfNotNull(Tasks.current());
    }
    
    @Override
    public synchronized String toString() {
        return super.toString()+"["
            + "owningTasks="+owningTasks
            + "; requestingTasks="+requestingTasks+"]";
    }
    
}
