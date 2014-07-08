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
package brooklyn.location.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.management.ManagementContext;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

/**
 * Allows you to say, in your brooklyn.properties:
 * 
 * brooklyn.location.named.foo=localhost
 * brooklyn.location.named.foo.user=bob
 * brooklyn.location.named.foo.privateKeyFile=~/.ssh/custom-key-for-bob
 * brooklyn.location.named.foo.privateKeyPassphrase=WithAPassphrase
 * <p>
 * or
 * <p>
 * brooklyn.location.named.bob-aws-east=jclouds:aws-ec2:us-east-1
 * brooklyn.location.named.bob-aws-east.identity=BobId
 * brooklyn.location.named.bob-aws-east.credential=BobCred
 * <p>
 * then you can simply refer to:   foo   or   named:foo   (or bob-aws-east or named:bob-aws-east)   in any location spec
 */
public class NamedLocationResolver implements LocationResolver {

    public static final Logger log = LoggerFactory.getLogger(NamedLocationResolver.class);

    public static final String NAMED = "named";
    
    @SuppressWarnings("unused")
    private ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }
    
    @SuppressWarnings({ "rawtypes" })
    public Location newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        String name = spec;
        ConfigBag lfBag = ConfigBag.newInstance(locationFlags).putIfAbsent(LocationInternal.ORIGINAL_SPEC, name);
        name = Strings.removeFromStart(spec, getPrefix()+":");
        if (name.toLowerCase().startsWith(NAMED+":")) {
            // since 0.7.0
            log.warn("Deprecated use of 'named:' prefix with wrong case ("+spec+"); support may be removed in future versions");
            name = spec.substring( (NAMED+":").length() );
        }
        
        LocationDefinition ld = registry.getDefinedLocationByName(name);
        if (ld==null) throw new NoSuchElementException("No named location defined matching '"+name+"'");
        return ((BasicLocationRegistry)registry).resolveLocationDefinition(ld, lfBag.getAllConfig(), name);
    }

    @Override
    public String getPrefix() {
        return NAMED;
    }
    
    /** accepts anything starting  named:xxx  or  xxx where xxx is a defined location name */
    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        if (BasicLocationRegistry.isResolverPrefixForSpec(this, spec, false)) return true;
        if (registry.getDefinedLocationByName(spec)!=null) return true;
        return false;
    }

}
