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
package org.apache.brooklyn.camp.spi.instantiate;

import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.spi.Assembly;
import org.apache.brooklyn.camp.spi.AssemblyTemplate;

/** A simple instantiator which simply invokes the component's instantiators in parallel */
public class BasicAssemblyTemplateInstantiator implements AssemblyTemplateInstantiator {

    public Assembly instantiate(AssemblyTemplate template, CampPlatform platform) {
        // TODO this should build it based on the components
//        template.getPlatformComponentTemplates().links().iterator().next().resolve();
        
        // platforms should set a bunch of instantiators, or else let the ComponentTemplates do this!
        throw new UnsupportedOperationException("No instantiator could be found which understands the submitted plan. Basic instantiator not yet supported.");
    }

}
