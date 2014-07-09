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
package io.brooklyn.camp.spi.collection;

import io.brooklyn.camp.spi.AbstractResource;

public abstract class AbstractResourceLookup<T extends AbstractResource> implements ResourceLookup<T> {

    /** convenience for concrete subclasses */
    protected ResolvableLink<T> newLink(String id, String name) {
        return new ResolvableLink<T>(id, name, this);
    }

    @Override
    public boolean isEmpty() {
        return links().isEmpty();
    }

}
