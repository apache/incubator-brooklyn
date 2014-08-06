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
package brooklyn.basic;

import java.util.Set;

import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.text.Identifiers;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public abstract class AbstractBrooklynObject implements BrooklynObjectInternal {

    @SetFromFlag(value="id")
    private String id = Identifiers.makeRandomId(8);
    
    /** subclasses should synchronize on this for all access */
    @SetFromFlag(value="tags")
    protected Set<Object> tags = Sets.newLinkedHashSet();
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public Set<Object> getTags() {
        synchronized (tags) {
            return ImmutableSet.copyOf(tags);
        }
    }

    public boolean addTag(Object tag) {
        boolean result;
        synchronized (tags) {
            result = tags.add(tag);
        }
        onTagsChanged();
        return result;
    }    

    public boolean addTags(Iterable<Object> tags) {
        boolean result;
        synchronized (tags) {
            result = Iterables.addAll(this.tags, tags);
        }
        onTagsChanged();
        return result;
    }    

    public boolean removeTag(Object tag) {
        boolean result;
        synchronized (tags) {
            result = tags.remove(tag);
        }
        onTagsChanged();
        return result;
    }    

    public boolean containsTag(Object tag) {
        synchronized (tags) {
            return tags.contains(tag);
        }
    }    

    protected abstract void onTagsChanged();
    
}
