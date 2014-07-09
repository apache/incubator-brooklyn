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
/* CityLookupTest.java */

import com.maxmind.geoip.*;
import java.io.IOException;

/* sample of how to use the GeoIP Java API with GeoIP City database */
/* Usage: java CityLookupTest 64.4.4.4 */

class CityLookupTest {
    public static void main(String[] args) {
	try {
	    LookupService cl = new LookupService("/usr/local/share/GeoIP/GeoIPCity.dat",
					LookupService.GEOIP_MEMORY_CACHE );
            Location l1 = cl.getLocation("213.52.50.8");
            Location l2 = cl.getLocation(args[0]);
	    System.out.println("countryCode: " + l2.countryCode +
                               "\n countryName: " + l2.countryName +
                               "\n region: " + l2.region +
                               "\n regionName: " + regionName.regionNameByCode(l2.countryCode, l2.region) +
                               "\n city: " + l2.city +
                               "\n postalCode: " + l2.postalCode +
                               "\n latitude: " + l2.latitude +
                               "\n longitude: " + l2.longitude +
                               "\n distance: " + l2.distance(l1) +
                               "\n distance: " + l1.distance(l2) + 
 			       "\n metro code: " + l2.metro_code +
 			       "\n area code: " + l2.area_code +
                               "\n timezone: " + timeZone.timeZoneByCountryAndRegion(l2.countryCode, l2.region));

	    cl.close();
	}
	catch (IOException e) {
	    System.out.println("IO Exception");
	}
    }
}
