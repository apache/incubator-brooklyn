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
package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.mementos.BrooklynMementoPersister.LookupContext;

public class RetryingMementoSerializer<T> implements MementoSerializer<T> {
    
    private static final Logger LOG = LoggerFactory.getLogger(RetryingMementoSerializer.class);
    
    private final MementoSerializer<T> delegate;
    private final int maxAttempts;
    
    public RetryingMementoSerializer(MementoSerializer<T> delegate, int maxAttempts) {
        this.delegate = checkNotNull(delegate, "delegate");
        this.maxAttempts = maxAttempts;
        if (maxAttempts < 1) throw new IllegalArgumentException("Max attempts must be at least 1, but was "+maxAttempts);
    }
    
    @Override
    public String toString(T memento) {
        RuntimeException lastException = null;
        int attempt = 0;
        do {
            attempt++;
            try {
                return delegate.toString(memento);
            } catch (RuntimeException e) {
                LOG.warn("Error serializing memento (attempt "+attempt+" of "+maxAttempts+") for "+memento+
                        "; expected sometimes if attribute value modified", e);
                lastException = e;
            }
        } while (attempt < maxAttempts);
        
        throw lastException;
    }
    
    @Override
    public T fromString(String string) {
        RuntimeException lastException = null;
        int attempt = 0;
        do {
            attempt++;
            try {
                return delegate.fromString(string);
            } catch (RuntimeException e) {
                LOG.warn("Error deserializing memento (attempt "+attempt+" of "+maxAttempts+")", e);
                lastException = e;
            }
        } while (attempt < maxAttempts);
        
        throw lastException;
    }

    @Override
    public void setLookupContext(LookupContext lookupContext) {
        delegate.setLookupContext(lookupContext);
    }

    @Override
    public void unsetLookupContext() {
        delegate.unsetLookupContext();
    }
}
