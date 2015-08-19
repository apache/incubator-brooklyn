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
package org.apache.brooklyn.core.mgmt;

import java.io.Serializable;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.annotations.Beta;

/** @since 0.7.0 some strongly typed tags for reference; note these may migrate elsewhere! */
@Beta
public class BrooklynTags {

    public static final String YAML_SPEC_KIND = "yaml_spec";
    public static final String NOTES_KIND = "notes";
    
    public static class NamedStringTag implements Serializable {
        private static final long serialVersionUID = 7932098757009051348L;
        @JsonProperty final String kind;
        @JsonProperty final String contents;
        public NamedStringTag(@JsonProperty("kind") String kind, @JsonProperty("contents") String contents) {
            this.kind = kind;
            this.contents = contents;
        }
        @Override
        public String toString() {
            return kind+"["+contents+"]";
        }
        
        public String getKind() {
            return kind;
        }
        public String getContents() {
            return contents;
        }
    }
    
    public static NamedStringTag newYamlSpecTag(String contents) { return new NamedStringTag(YAML_SPEC_KIND, contents); }
    public static NamedStringTag newNotesTag(String contents) { return new NamedStringTag(NOTES_KIND, contents); }
    
    public static NamedStringTag findFirst(String kind, Iterable<Object> tags) {
        for (Object object: tags) {
            if (object instanceof NamedStringTag && kind.equals(((NamedStringTag)object).kind))
                return (NamedStringTag) object;
        }
        return null;
    }
    
}
