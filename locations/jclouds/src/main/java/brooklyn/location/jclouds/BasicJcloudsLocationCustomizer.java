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
package brooklyn.location.jclouds;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.TemplateOptions;

import com.google.common.annotations.Beta;


/**
 * A default no-op implementation, which can be extended to override the appropriate methods.
 * 
 * Sub-classing will give the user some protection against future API changes - note that 
 * {@link JcloudsLocationCustomizer} is marked {@link Beta}.
 * 
 * @author aled
 */
public class BasicJcloudsLocationCustomizer implements JcloudsLocationCustomizer {

    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, TemplateBuilder templateBuilder) {
        // no-op
    }

    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, Template template) {
        // no-op
    }

    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, TemplateOptions templateOptions) {
        // no-op
    }

    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, JcloudsSshMachineLocation machine) {
        // no-op
    }
}
