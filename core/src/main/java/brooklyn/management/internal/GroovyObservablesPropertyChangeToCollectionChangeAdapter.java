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

import groovy.util.ObservableList;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class GroovyObservablesPropertyChangeToCollectionChangeAdapter implements PropertyChangeListener {
    @SuppressWarnings("rawtypes")
    private final CollectionChangeListener delegate;

    public GroovyObservablesPropertyChangeToCollectionChangeAdapter(@SuppressWarnings("rawtypes") CollectionChangeListener delegate) {
        this.delegate = delegate;
    }

    @SuppressWarnings("unchecked")
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt instanceof ObservableList.ElementAddedEvent) {
            delegate.onItemAdded(evt.getNewValue());
        } else if (evt instanceof ObservableList.ElementRemovedEvent) {
            delegate.onItemRemoved(evt.getOldValue());
        } else if (evt instanceof ObservableList.ElementUpdatedEvent) {
            delegate.onItemRemoved(evt.getOldValue());
            delegate.onItemAdded(evt.getNewValue());
        } else if (evt instanceof ObservableList.ElementClearedEvent) {
            for (Object value : ((ObservableList.ElementClearedEvent) evt).getValues()) {
                delegate.onItemAdded(value);
            }
        } else if(evt instanceof ObservableList.MultiElementAddedEvent ) {
            for(Object value: ((ObservableList.MultiElementAddedEvent)evt).getValues()){
                delegate.onItemAdded(value);
            }
        }
    }

    public int hashCode() {
        return delegate.hashCode();
    }

    public boolean equals(Object other) {
        if (other instanceof GroovyObservablesPropertyChangeToCollectionChangeAdapter)
            return delegate.equals(((GroovyObservablesPropertyChangeToCollectionChangeAdapter) other).delegate);
        if (other instanceof CollectionChangeListener)
            return delegate.equals(other);
        return false;
    }
} 