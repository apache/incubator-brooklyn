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
package org.apache.brooklyn.rest.apidoc;

import java.util.Comparator;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

import com.wordnik.swagger.core.DocumentationEndPoint;

@JsonIgnoreProperties({
    "com$wordnik$swagger$core$DocumentationEndPoint$$ops"
})
public class ApidocEndpoint extends DocumentationEndPoint {

    public static final Comparator<ApidocEndpoint> COMPARATOR = new Comparator<ApidocEndpoint>() {
        @Override
        public int compare(ApidocEndpoint o1, ApidocEndpoint o2) {
            if (o1.name==o2.name) return 0;
            if (o1.name==null) return -1;
            if (o2.name==null) return 1;
            return o1.name.compareTo(o2.name);
        }
    };
    
    public final String name;
    public final String link;
    
    @JsonCreator
    public ApidocEndpoint(@JsonProperty("name") String name, @JsonProperty("path") String path, @JsonProperty("description") String description, @JsonProperty("link") String link) {
        super(path, description);
        this.name = name;
        this.link = link;
    }
    
}
