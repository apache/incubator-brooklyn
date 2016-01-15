/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.brooklyn.rest.transform;

import com.google.common.base.Function;

import org.apache.brooklyn.core.BrooklynVersion.BrooklynFeature;
import org.apache.brooklyn.rest.domain.BrooklynFeatureSummary;

public class BrooklynFeatureTransformer {

    public static final Function<BrooklynFeature, BrooklynFeatureSummary> FROM_FEATURE = new Function<BrooklynFeature, BrooklynFeatureSummary>() {
        @Override
        public BrooklynFeatureSummary apply(BrooklynFeature feature) {
            return featureSummary(feature);
        }
    };

    public static BrooklynFeatureSummary featureSummary(BrooklynFeature feature) {
        return new BrooklynFeatureSummary(
                feature.getName(),
                feature.getSymbolicName(),
                feature.getVersion(),
                feature.getLastModified(),
                feature.getAdditionalData());
    }

}
