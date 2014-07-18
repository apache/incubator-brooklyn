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
package io.brooklyn.camp.spi.resolve;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.BasicAssemblyTemplateInstantiator;
import io.brooklyn.camp.spi.pdp.Artifact;
import io.brooklyn.camp.spi.pdp.AssemblyTemplateConstructor;
import io.brooklyn.camp.spi.pdp.DeploymentPlan;
import io.brooklyn.camp.spi.pdp.Service;
import io.brooklyn.camp.spi.resolve.interpret.PlanInterpretationContext;
import io.brooklyn.camp.spi.resolve.interpret.PlanInterpretationNode;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.yaml.snakeyaml.error.YAMLException;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.yaml.Yamls;

import com.google.common.annotations.VisibleForTesting;

public class PdpProcessor {

    final CampPlatform campPlatform;
    
    final List<PdpMatcher> matchers = new ArrayList<PdpMatcher>();
    final List<PlanInterpreter> interpreters = new ArrayList<PlanInterpreter>();
    
    public PdpProcessor(CampPlatform campPlatform) {
        this.campPlatform = campPlatform;
    }

    @SuppressWarnings("unchecked")
    public DeploymentPlan parseDeploymentPlan(Reader yaml) {
        Iterable<Object> template = Yamls.parseAll(yaml);
        
        Map<String, Object> dpRootUninterpreted = null;
        try {
            dpRootUninterpreted = Yamls.getAs(template, Map.class);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            throw new YAMLException("Plan not in acceptable format: "+(e.getMessage()!=null ? e.getMessage() : ""+e), e);
        }
        Map<String, Object> dpRootInterpreted = applyInterpreters(dpRootUninterpreted);
        
		return DeploymentPlan.of( dpRootInterpreted );
    }
    
    /** create and return an AssemblyTemplate based on the given DP (yaml) */
    public AssemblyTemplate registerDeploymentPlan(Reader yaml) {
        DeploymentPlan plan = parseDeploymentPlan(yaml);
        return registerDeploymentPlan(plan);
    }

    /** applies matchers to the given deployment plan to create an assembly template */
    public AssemblyTemplate registerDeploymentPlan(DeploymentPlan plan) {
        AssemblyTemplateConstructor atc = new AssemblyTemplateConstructor(campPlatform);
        
        if (plan.getName()!=null) atc.name(plan.getName());
        if (plan.getDescription()!=null) atc.description(plan.getDescription());
        // nothing done with origin just now...
        
        if (plan.getServices()!=null) {
            for (Service svc: plan.getServices()) {
                applyMatchers(svc, atc);
            }
        }

        if (plan.getArtifacts()!=null) {
            for (Artifact art: plan.getArtifacts()) {
                applyMatchers(art, atc);
            }
        }

        Map<String, Object> attrs = plan.getCustomAttributes();
        if (attrs!=null && !attrs.isEmpty())
            atc.addCustomAttributes(attrs);
        
        if (atc.getInstantiator()==null)
            // set a default instantiator which just invokes the component's instantiators
            // (or throws unsupported exceptions, currently!)
            atc.instantiator(BasicAssemblyTemplateInstantiator.class);

        return atc.commit();
    }
    
    public AssemblyTemplate registerPdpFromArchive(InputStream archiveInput) {
        try {
            ArchiveInputStream input = new ArchiveStreamFactory()
                .createArchiveInputStream(archiveInput);
            
            while (true) {
                ArchiveEntry entry = input.getNextEntry();
                if (entry==null) break;
                // TODO unpack entry, create a space on disk holding the archive ?
            }

            // use yaml...
            throw new UnsupportedOperationException("in progress");
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }


    // ----------------------------
    
    public void addMatcher(PdpMatcher m) {
        // TODO a list is a crude way to do matching ... but good enough to start
        matchers.add(m);
    }

    public List<PdpMatcher> getMatchers() {
        return matchers;
    }


    protected void applyMatchers(Object deploymentPlanItem, AssemblyTemplateConstructor atc) {
        for (PdpMatcher matcher: getMatchers()) {
            if (matcher.accepts(deploymentPlanItem)) {
                // TODO first accepting is a crude way to do matching ... but good enough to start
                if (matcher.apply(deploymentPlanItem, atc))
                    return;
            }
        }
        throw new UnsupportedOperationException("Deployment plan item "+deploymentPlanItem+" cannot be matched");
    }

    // ----------------------------

    public void addInterpreter(PlanInterpreter interpreter) {
    	interpreters.add(interpreter);
    }
    
    /** returns a DeploymentPlan object which is the result of running the interpretation
     * (with all interpreters) against the supplied deployment plan YAML object,
     * essentially a post-parse processing step before matching */
    @SuppressWarnings("unchecked")
    @VisibleForTesting
	public Map<String, Object> applyInterpreters(Map<String, Object> originalDeploymentPlan) {
    	PlanInterpretationNode interpretation = new PlanInterpretationNode(
    			new PlanInterpretationContext(originalDeploymentPlan, interpreters));
		return (Map<String, Object>) interpretation.getNewValue();
    }
    
}
