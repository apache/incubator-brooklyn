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

import java.net.InetAddress;
import java.util.Collection;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation.LocalhostMachine;
import brooklyn.util.guava.Maybe;

/** utilities for working with MachineLocations */
public class Machines {

    private static final Logger log = LoggerFactory.getLogger(Machines.class);
    
    public static Maybe<String> getSubnetHostname(Location where) {
        String hostname = null;
        if (where instanceof HasSubnetHostname) {
            hostname = ((HasSubnetHostname) where).getSubnetHostname();
        }
        if (hostname == null && where instanceof MachineLocation) {
            InetAddress addr = ((MachineLocation) where).getAddress();
            if (addr != null) hostname = addr.getHostAddress();
        }
        log.debug("computed hostname {} for {}", hostname, where);
        // TODO if Maybe.absent(message) appears, could/should use that
        // TODO If no machine available, should we throw new IllegalStateException("Cannot find hostname for "+where);
        return Maybe.fromNullable(hostname);
    }

    public static Maybe<String> getSubnetIp(Location where) {
        // TODO Too much duplication between the ip and hostname methods
        String result = null;
        if (where instanceof HasSubnetHostname) {
            result = ((HasSubnetHostname) where).getSubnetIp();
        }
        if (result == null && where instanceof MachineLocation) {
            InetAddress addr = ((MachineLocation) where).getAddress();
            if (addr != null) result = addr.getHostAddress();
        }
        log.debug("computed hostname {} for {}", result, where);
        return Maybe.fromNullable(result);
    }

    @SuppressWarnings("unchecked")
    public static <T> Maybe<T> findUniqueElement(Iterable<?> items, Class<T> type) {
        if (items==null) return null;
        Iterator<?> i = items.iterator();
        T result = null;
        while (i.hasNext()) {
            Object candidate = i.next();
            if (type.isInstance(candidate)) {
                if (result==null) result = (T)candidate;
                else {
                    if (log.isTraceEnabled())
                        log.trace("Multiple instances of "+type+" in "+items+"; ignoring");
                    return Maybe.absent(new IllegalStateException("Multiple instances of "+type+" in "+items+"; expected a single one"));
                }
            }
        }
        if (result==null) 
            return Maybe.absent(new IllegalStateException("No instances of "+type+" available (in "+items+")"));
        return Maybe.of(result);
    }
    
    public static Maybe<MachineLocation> findUniqueMachineLocation(Iterable<? extends Location> locations) {
        return findUniqueElement(locations, MachineLocation.class);
    }

    public static Maybe<SshMachineLocation> findUniqueSshMachineLocation(Iterable<? extends Location> locations) {
        return findUniqueElement(locations, SshMachineLocation.class);
    }

    public static Maybe<String> findSubnetHostname(Iterable<? extends Location> ll) {
        Maybe<MachineLocation> l = findUniqueMachineLocation(ll);
        if (!l.isPresent()) {
            return Maybe.absent();
//            throw new IllegalStateException("Cannot find hostname for among "+ll);
        }
        return Machines.getSubnetHostname(l.get());
    }

    public static Maybe<String> findSubnetHostname(Entity entity) {
        String sh = entity.getAttribute(Attributes.SUBNET_HOSTNAME);
        if (sh!=null) return Maybe.of(sh);
        return findSubnetHostname(entity.getLocations());
    }
    
    public static Maybe<String> findSubnetOrPublicHostname(Entity entity) {
        String hn = entity.getAttribute(Attributes.HOSTNAME);
        if (hn!=null) {
            // attributes already set, see if there was a SUBNET_HOSTNAME set
            // note we rely on (public) hostname being set _after_ subnet_hostname,
            // to prevent tiny possibility of races resulting in hostname being returned
            // becasue subnet is still being looked up -- see MachineLifecycleEffectorTasks
            Maybe<String> sn = findSubnetHostname(entity);
            if (sn.isPresent()) return sn;
            // short-circuit discovery if attributes have been set already
            return Maybe.of(hn);
        }
        
        Maybe<MachineLocation> l = findUniqueMachineLocation(entity.getLocations());
        if (!l.isPresent()) return Maybe.absent();
        InetAddress addr = l.get().getAddress();
        if (addr==null) return Maybe.absent();
        return Maybe.fromNullable(addr.getHostName());
    }

    public static Maybe<String> findSubnetOrPrivateIp(Entity entity) {
        // see comments in findSubnetOrPrivateHostname
        String hn = entity.getAttribute(Attributes.ADDRESS);
        if (hn!=null) {
            Maybe<String> sn = findSubnetIp(entity);
            if (sn.isPresent()) return sn;
            return Maybe.of(hn);
        }
        
        Maybe<MachineLocation> l = findUniqueMachineLocation(entity.getLocations());
        if (!l.isPresent()) return Maybe.absent();
        InetAddress addr = l.get().getAddress();
        if (addr==null) return Maybe.absent();
        return Maybe.fromNullable(addr.getHostAddress());
    }

    public static Maybe<String> findSubnetIp(Entity entity) {
        String sh = entity.getAttribute(Attributes.SUBNET_ADDRESS);
        if (sh!=null) return Maybe.of(sh);
        return findSubnetIp(entity.getLocations());
    }
    
    public static Maybe<String> findSubnetIp(Iterable<? extends Location> ll) {
        // TODO Or if can't find MachineLocation, should we throw new IllegalStateException("Cannot find hostname for among "+ll);
        Maybe<MachineLocation> l = findUniqueMachineLocation(ll);
        return (l.isPresent()) ? Machines.getSubnetIp(l.get()) : Maybe.<String>absent();
    }

    /** returns whether it is localhost (and has warned) */
    public static boolean warnIfLocalhost(Collection<? extends Location> locations, String message) {
        if (locations.size()==1) {
            Location l = locations.iterator().next();
            if (l instanceof LocalhostMachineProvisioningLocation || l instanceof LocalhostMachine) {
                log.warn(message);
                return true;
            }
        }
        return false;
    }
    
}
