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
package brooklyn.entity.drivers.downloads;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadTargets;
import brooklyn.util.collections.MutableList;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class BasicDownloadTargets implements DownloadTargets {

    private static final DownloadTargets EMPTY = builder().build();
    
    public static DownloadTargets empty() {
        return EMPTY;
    }
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> primaries = Lists.newArrayList();
        private List<String> fallbacks = Lists.newArrayList();
        private boolean canContinueResolving = true;
        
        public Builder addAll(DownloadTargets other) {
            addPrimaries(other.getPrimaryLocations());
            addFallbacks(other.getFallbackLocations());
            return this;
        }
        
        public Builder addPrimary(String val) {
            checkNotNull(val, "val");
            if (!primaries.contains(val)) primaries.add(val);
            return this;
        }

        public Builder addPrimaries(Iterable<String> vals) {
            for (String val : checkNotNull(vals, "vals")) {
                addPrimary(val);
            }
            return this;
        }

        public Builder addFallback(String val) {
            checkNotNull(val, "val");
            if (!fallbacks.contains(val)) fallbacks.add(val);
            return this;
        }

        public Builder addFallbacks(Iterable<String> vals) {
            for (String val : checkNotNull(vals, "vals")) {
                addFallback(val);
            }
            return this;
        }

        public Builder canContinueResolving(boolean val) {
            canContinueResolving = val;
            return this;
        }
        
        public BasicDownloadTargets build() {
            return new BasicDownloadTargets(this);
        }
    }

    private final List<String> primaries;
    private final List<String> fallbacks;
    private final boolean canContinueResolving;
    
    protected BasicDownloadTargets(Builder builder) {
        primaries = ImmutableList.copyOf(builder.primaries);
        fallbacks = MutableList.<String>builder().addAll(builder.fallbacks).removeAll(builder.primaries).build().asUnmodifiable();
        canContinueResolving = builder.canContinueResolving;
    }

    @Override
    public List<String> getPrimaryLocations() {
        return primaries;
    }

    @Override
    public List<String> getFallbackLocations() {
        return fallbacks;
    }

    @Override
    public boolean canContinueResolving() {
        return canContinueResolving;
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("primaries", primaries).add("fallbacks", fallbacks)
                .add("canContinueResolving", canContinueResolving).toString();
    }
}
