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
package brooklyn.location.jclouds.pool;

import java.util.Map;

import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Processor;
import org.jclouds.domain.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;

public class MachinePoolPredicates {

    private static final Logger log = LoggerFactory.getLogger(MachinePoolPredicates.class);
    
    public static Predicate<NodeMetadata> except(final MachineSet removedItems) {
        return new Predicate<NodeMetadata>() {
            @Override
            public boolean apply(NodeMetadata input) {
                return !removedItems.contains(input);
            }
        };
    }

    public static Predicate<NodeMetadata> except(final Predicate<NodeMetadata> predicateToExclude) {
        return Predicates.not(predicateToExclude);
    }

    public static Predicate<NodeMetadata> matching(final ReusableMachineTemplate template) {
        return new Predicate<NodeMetadata>() {
            @Override
            public boolean apply(NodeMetadata input) {
                return matches(template, input);
            }
        };
    }

    public static Predicate<NodeMetadata> withTag(final String tag) {
        return new Predicate<NodeMetadata>() {
            @Override
            public boolean apply(NodeMetadata input) {
                return input.getTags().contains(tag);
            }
        };
    }

    public static Predicate<NodeMetadata> compose(final Predicate<NodeMetadata> ...predicates) {
        return Predicates.and(predicates);
    }

    /** True iff the node matches the criteria specified in this template. 
     * <p>
     * NB: This only checks some of the most common fields, 
     * plus a hashcode (in strict mode).  
     * In strict mode you're practically guaranteed to match only machines created by this template.
     * (Add a tag(uid) and you _will_ be guaranteed, strict mode or not.)
     * <p> 
     * Outside strict mode, some things (OS and hypervisor) can fall through the gaps.  
     * But if that is a problem we can easily add them in.
     * <p>
     * (Caveat: If explicit Hardware, Image, and/or Template were specified in the template,
     * then the hash code probably will not detect it.)   
     **/
    public static boolean matches(ReusableMachineTemplate template, NodeMetadata m) {
        try {
            // tags and user metadata

            if (! m.getTags().containsAll( template.getTags(false) )) return false;

            if (! isSubMapOf(template.getUserMetadata(false), m.getUserMetadata())) return false;


            // common hardware parameters

            if (template.getMinRam()!=null && m.getHardware().getRam() < template.getMinRam()) return false;

            if (template.getMinCores()!=null) {
                double numCores = 0;
                for (Processor p: m.getHardware().getProcessors()) numCores += p.getCores();
                if (numCores+0.001 < template.getMinCores()) return false;
            }

            if (template.getIs64bit()!=null) {
                if (m.getOperatingSystem().is64Bit() != template.getIs64bit()) return false;
            }

            if (template.getOsFamily()!=null) {
                if (m.getOperatingSystem() == null || 
                        !template.getOsFamily().equals(m.getOperatingSystem().getFamily())) return false;
            }
            if (template.getOsNameMatchesRegex()!=null) {
                if (m.getOperatingSystem() == null || m.getOperatingSystem().getName()==null ||
                        !m.getOperatingSystem().getName().matches(template.getOsNameMatchesRegex())) return false;
            }

            if (template.getLocationId()!=null) {
                if (!isLocationContainedIn(m.getLocation(), template.getLocationId())) return false;
            }

            // TODO other TemplateBuilder fields and TemplateOptions

            return true;
            
        } catch (Exception e) {
            log.warn("Error (rethrowing) trying to match "+m+" against "+template+": "+e, e);
            throw Throwables.propagate(e);
        }
    }

    private static boolean isLocationContainedIn(Location location, String locationId) {
        if (location==null) return false;
        if (locationId.equals(location.getId())) return true;
        return isLocationContainedIn(location.getParent(), locationId);
    }

    public static boolean isSubMapOf(Map<String, String> sub, Map<String, String> bigger) {
        for (Map.Entry<String, String> e: sub.entrySet()) {
            if (e.getValue()==null) {
                if (!bigger.containsKey(e.getKey())) return false;
                if (bigger.get(e.getKey())!=null) return false;
            } else {
                if (!e.getValue().equals(bigger.get(e.getKey()))) return false;
            }
        }
        return true;
    }

}
