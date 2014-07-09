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
package brooklyn.rest.domain;

import java.util.Map;

import javax.annotation.Nullable;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;

@Beta
public class LinkWithMetadata {

    // TODO remove 'metadata' and promote its contents to be top-level fields; then unmark as Beta
    
    private final String link;
    private final Map<String,Object> metadata;
    
    public LinkWithMetadata(
            @JsonProperty("link") String link, 
            @Nullable @JsonProperty("metadata") Map<String,?> metadata) {
        this.link = link;
        this.metadata = metadata==null ? ImmutableMap.<String,Object>of() : ImmutableMap.<String,Object>copyOf(metadata);
    }
    
    public String getLink() {
        return link;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((link == null) ? 0 : link.hashCode());
        result = prime * result + ((metadata == null) ? 0 : metadata.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LinkWithMetadata other = (LinkWithMetadata) obj;
        if (link == null) {
            if (other.link != null)
                return false;
        } else if (!link.equals(other.link))
            return false;
        if (metadata == null) {
            if (other.metadata != null)
                return false;
        } else if (!metadata.equals(other.metadata))
            return false;
        return true;
    }

    
}
