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
package org.apache.brooklyn.core.plan;

import java.util.Collection;
import java.util.ServiceLoader;

import org.apache.brooklyn.api.mgmt.ManagementContext;

import com.google.common.collect.Lists;

public class PlanToSpecFactory {
    public static PlanToSpecTransformer forMime(ManagementContext mgmt, String mime) {
        Collection<PlanToSpecTransformer> transformers = all(mgmt);
        for (PlanToSpecTransformer transformer : transformers) {
            if (transformer.accepts(mime)) {
                return transformer;
            }
        }
        throw new IllegalStateException("PlanToSpecTransformer for type " + mime + " not found. Registered transformers are: " + transformers);
    }

    public static Collection<PlanToSpecTransformer> all(ManagementContext mgmt) {
        Collection<PlanToSpecTransformer> transformers = Lists.newArrayList(ServiceLoader.load(PlanToSpecTransformer.class));
        for(PlanToSpecTransformer t : transformers) {
            t.injectManagementContext(mgmt);
        }
        return transformers;
    }
}
