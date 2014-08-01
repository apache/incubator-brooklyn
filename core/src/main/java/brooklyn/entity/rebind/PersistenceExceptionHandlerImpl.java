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
package brooklyn.entity.rebind;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.BrooklynObject;
import brooklyn.mementos.Memento;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.Sets;

public class PersistenceExceptionHandlerImpl implements PersistenceExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PersistenceExceptionHandlerImpl.class);
    
    private final AtomicBoolean active = new AtomicBoolean(true);

    private final Set<String> prevFailedMementoGenerators = Sets.newConcurrentHashSet();
    private final Set<String> prevFailedPersisters = Sets.newConcurrentHashSet();
    
    public static Builder builder() {
        return new Builder();
    }

    // Builder is here to copy pattern in RebindExceptionHandler, and to make it easier to edit API in the future
    public static class Builder {
        public PersistenceExceptionHandler build() {
            return new PersistenceExceptionHandlerImpl(this);
        }
    }
    
    public PersistenceExceptionHandlerImpl(Builder builder) {
    }

    @Override
    public void stop() {
        active.set(false);
    }
    
    @Override
    public void onGenerateMementoFailed(BrooklynObjectType type, BrooklynObject instance, Exception e) {
        String errmsg = "generate memento for "+type+" "+instance.getClass().getName()+"("+instance.getId()+")";
        onErrorImpl(errmsg, e, prevFailedMementoGenerators.add(instance.getId()));
    }
    
    @Override
    public void onPersistMementoFailed(Memento memento, Exception e) {
        String errmsg = "persist for "+memento.getClass().getSimpleName()+" "+memento.getType()+"("+memento.getId()+")";
        onErrorImpl(errmsg, e, prevFailedPersisters.add(memento.getId()));
    }
    
    @Override
    public void onDeleteMementoFailed(String id, Exception e) {
        String errmsg = "delete for memento "+id;
        onErrorImpl(errmsg, e, prevFailedPersisters.add(id));
    }
    
    protected void onErrorImpl(String errmsg, Exception e, boolean isRepeat) {
        Exceptions.propagateIfFatal(e);
        if (isActive()) {
            if (isRepeat) {
                if (LOG.isDebugEnabled()) LOG.debug("Repeating problem: "+errmsg, e);
            } else {
                LOG.warn("Problem: "+errmsg, e);
            }
        } else {
            if (isRepeat) {
                if (LOG.isTraceEnabled()) LOG.trace("Repeating problem: "+errmsg+"; but no longer active (ignoring)", e);
            } else {
                if (LOG.isDebugEnabled()) LOG.debug("Problem: "+errmsg+"; but no longer active (ignoring)", e);
            }
        }
    }

    protected boolean isActive() {
        return active.get();
    }
}
