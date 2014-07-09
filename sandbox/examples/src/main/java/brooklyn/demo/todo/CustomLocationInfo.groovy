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
package brooklyn.demo.todo;

import java.util.List;
import java.util.Map

import brooklyn.location.Location;
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.CommandLineLocations
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation

public class CustomLocationInfo extends CommandLineLocations {

    public static final Map LOCALHOST_COORDS = [
        id : LOCALHOST,
        displayName : "Localhost",
        streetAddress : "Appleton Tower, Edinburgh",
        latitude : 55.94944, longitude : -3.16028,
        iso3166 : "GB-EDH" ]
    public static final String MONTEREY_EAST = "monterey-east"
    public static final Map MONTEREY_EAST_COORDS = [
        id : MONTEREY_EAST,
        displayName : "Hawthorne, NY",
        streetAddress : "Hawthorne, NY",
        latitude : 41.10361, longitude : -73.79583,
        iso3166 : "US-NY" ]
    public static final String EDINBURGH = "edinburgh"
    public static final Map EDINBURGH_COORDS = [
        id : EDINBURGH,
        displayName : "HQ, Edinburgh",
        streetAddress : "Appleton Tower, Edinburgh",
        latitude : 55.94944, longitude : -3.16028,
        iso3166 : "GB-EDH" ]
    public static final String MAGENTA = "magenta"
    public static final Map MAGENTA_COORDS = [
        id : LOCALHOST,
        displayName : "Magenta",
        streetAddress : "Torphichen Place, Edinburgh",
        latitude : 55.94944, longitude : -3.16028,
        iso3166 : "GB-EDH" ]
    
    public static LocalhostMachineProvisioningLocation newLocalhostLocation(int numberOfInstances=0) {
        return new LocalhostMachineProvisioningLocation(
            count: numberOfInstances,
            latitude : LOCALHOST_COORDS['latitude'],
            longitude : LOCALHOST_COORDS['longitude'],
            displayName : 'Localhost',
            iso3166 : [LOCALHOST_COORDS['iso3166']]
        )
    }

    public static FixedListMachineProvisioningLocation newMontereyEastLocation() {
        // The definition of the Monterey East location
        final Collection<SshMachineLocation> MONTEREY_EAST_PUBLIC_ADDRESSES = [
                '216.48.127.224', '216.48.127.225', // east1a/b
                '216.48.127.226', '216.48.127.227', // east2a/b
                '216.48.127.228', '216.48.127.229', // east3a/b
                '216.48.127.230', '216.48.127.231', // east4a/b
                '216.48.127.232', '216.48.127.233', // east5a/b
                '216.48.127.234', '216.48.127.235'  // east6a/b
            ].collect { new SshMachineLocation(address:InetAddress.getByName(it), userName:'cdm') }

        MachineProvisioningLocation<SshMachineLocation> result =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines : MONTEREY_EAST_PUBLIC_ADDRESSES,
                latitude : MONTEREY_EAST_COORDS['latitude'],
                longitude : MONTEREY_EAST_COORDS['longitude'],
                displayName : 'Monterey East'
            )
        return result
    }

    public static FixedListMachineProvisioningLocation newMontereyEdinburghLocation() {
        // The definition of the Monterey Edinburgh location
        final Collection<SshMachineLocation> MONTEREY_EDINBURGH_MACHINES = [
                '192.168.144.241',
                '192.168.144.242',
                '192.168.144.243',
                '192.168.144.244',
                '192.168.144.245',
                '192.168.144.246'
            ].collect { new SshMachineLocation(address:InetAddress.getByName(it), userName:'cloudsoft') }

        MachineProvisioningLocation<SshMachineLocation> result =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines : MONTEREY_EDINBURGH_MACHINES,
                latitude : EDINBURGH_COORDS['latitude'],
                longitude : EDINBURGH_COORDS['longitude'],
                displayName : 'Monterey Edinburgh'
            )
        return result
    }

    /** Andrew's Magenta Cluster Location */
    public static FixedListMachineProvisioningLocation newMagentaClusterLocation() {
        final Collection<SshMachineLocation> MAGENTA_CLUSTER_MACHINES = [
                "aqua",
                "black",
                "blue",
                "fuchsia",
                "gray",
                "green",
                "lime",
                "maroon",
                "navy",
                "olive",
                "purple",
                "red",
                "silver",
                "yellow",
                // Not enough physical RAM for all 16 VMs
                // "teal", "white",
            ].collect { new SshMachineLocation(address:InetAddress.getByName(it), userName:"root") }

        MachineProvisioningLocation<SshMachineLocation> result =
            new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines : MAGENTA_CLUSTER_MACHINES,
                latitude : MAGENTA_COORDS["latitude"],
                longitude : MAGENTA_COORDS["longitude"],
                displayName : "Magenta Cluster"
            )
        return result
    }

    //FIXME should use a map/registry, to which we can add more    
    public static List<Location> getLocationsById(List<String> ids) {
        List<Location> locations = ids.collect { String location ->
            if (LOCALHOST == location) {
                newLocalhostLocation()
            } else if (MONTEREY_EAST == location) {
                newMontereyEastLocation()
            } else if (EDINBURGH == location) {
                newMontereyEdinburghLocation()
            } else if (MAGENTA == location) {
                newMagentaClusterLocation()
            } else if (LOCALHOST == location) {
                newLocalhostLocation()
            } else {
                super.getLocationsById(id);
            }
        }
        return locations
    }

}
