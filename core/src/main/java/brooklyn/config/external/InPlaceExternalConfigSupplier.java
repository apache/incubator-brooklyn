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
package brooklyn.config.external;

import java.util.Map;


/**
 * Instances are populated via sub-keys specified directly in the <tt>brooklyn.properties</tt> file:
 *
 * <pre>
 * brooklyn.external.foo = brooklyn.management.config.external.InPlaceConfigSupplier
 * brooklyn.external.foo.key1 = value1
 * brooklyn.external.foo.key2 = value2
 * </pre>
 *
 * This will instantiate an <code>InPlaceExternalConfigSupplier</code> populated with values for <code>key1</code>
 * and <code>key2</code>. Note that the <code>brooklyn.external.&lt;name&gt;</code> prefix is stripped.
 */
public class InPlaceExternalConfigSupplier extends AbstractExternalConfigSupplier {

    private final Map<?,?> config;

    public InPlaceExternalConfigSupplier(String name, Map<?, ?> config) {
        super(name);
        this.config = config;
    }

    public String get(String key) {
        return (String) config.get(key);
    }

}
