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
package io.brooklyn.camp.brooklyn.spi.creation;

import java.io.StringReader;

import brooklyn.basic.AbstractBrooklynObjectSpec;
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Application;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.plan.PlanToSpecTransformer;
import brooklyn.util.exceptions.Exceptions;
import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;

public class CampToSpecTransformer implements PlanToSpecTransformer {

    public static final String YAML_CAMP_PLAN_TYPE = "brooklyn.camp/yaml";

    private ManagementContext mgmt;

    @Override
    public String getName() {
        return YAML_CAMP_PLAN_TYPE;
    }

    @Override
    public boolean accepts(String mime) {
        return YAML_CAMP_PLAN_TYPE.equals(mime);
    }

    @Override
    public <T extends Application> EntitySpec<T> createApplicationSpec(String plan) {
      CampPlatform camp = CampCatalogUtils.getCampPlatform(mgmt);
      AssemblyTemplate at = camp.pdp().registerDeploymentPlan( new StringReader(plan) );
      AssemblyTemplateInstantiator instantiator;
      try {
          instantiator = at.getInstantiator().newInstance();
      } catch (Exception e) {
          throw Exceptions.propagate(e);
      }
      if (instantiator instanceof AssemblyTemplateSpecInstantiator) {
          BrooklynClassLoadingContext loader = JavaBrooklynClassLoadingContext.create(mgmt);
          @SuppressWarnings("unchecked")
          EntitySpec<T> createSpec = (EntitySpec<T>) ((AssemblyTemplateSpecInstantiator) instantiator).createSpec(at, camp, loader, true);
          return createSpec;
      } else {
          // The unknown instantiator can create the app (Assembly), but not a spec.
          // Currently, all brooklyn plans should produce the above.
          if (at.getPlatformComponentTemplates()==null || at.getPlatformComponentTemplates().isEmpty()) {
              if (at.getCustomAttributes().containsKey("brooklyn.catalog"))
                  throw new IllegalArgumentException("Unrecognized application blueprint format: expected an application, not a brooklyn.catalog");
              throw new IllegalArgumentException("Unrecognized application blueprint format: no services defined");
          }
          // map this (expected) error to a nicer message
          throw new IllegalArgumentException("Unrecognized application blueprint format");
      }
    }

    @Override
    public AbstractBrooklynObjectSpec<?, ?> createCatalogSpec(CatalogItem<?, ?> item) {
        return CampCatalogUtils.createSpec(mgmt, item);
    }

    @Override
    public void injectManagementContext(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

}
