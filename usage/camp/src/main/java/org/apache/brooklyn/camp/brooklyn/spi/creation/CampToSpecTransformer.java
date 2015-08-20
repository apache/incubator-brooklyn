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
package org.apache.brooklyn.camp.brooklyn.spi.creation;

import java.io.StringReader;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.brooklyn.api.AssemblyTemplateSpecInstantiator;
import org.apache.brooklyn.camp.spi.AssemblyTemplate;
import org.apache.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;
import org.apache.brooklyn.core.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.mgmt.classloading.JavaBrooklynClassLoadingContext;
import org.apache.brooklyn.core.plan.PlanToSpecTransformer;
import org.apache.brooklyn.util.exceptions.Exceptions;

public class CampToSpecTransformer implements PlanToSpecTransformer {

    public static final String YAML_CAMP_PLAN_TYPE = "org.apache.brooklyn.camp/yaml";

    private ManagementContext mgmt;

    @Override
    public String getShortDescription() {
        return "Brooklyn OASIS CAMP interpreter";
    }

    @Override
    public boolean accepts(String mime) {
        return YAML_CAMP_PLAN_TYPE.equals(mime);
    }

    @Override
    public EntitySpec<? extends Application> createApplicationSpec(String plan) {
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
          return ((AssemblyTemplateSpecInstantiator) instantiator).createSpec(at, camp, loader, true);
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

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public <T, SpecT extends AbstractBrooklynObjectSpec<T, SpecT>> AbstractBrooklynObjectSpec<T, SpecT> createCatalogSpec(CatalogItem<T, SpecT> item) {
        return (AbstractBrooklynObjectSpec<T, SpecT>) CampCatalogUtils.createSpec(mgmt, (CatalogItem)item);
    }

    @Override
    public void injectManagementContext(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

}
