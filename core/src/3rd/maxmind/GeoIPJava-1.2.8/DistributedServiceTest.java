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
// Usage: CLASSPATH=".:source" java DistributedServiceTest LICENSE_KEY 24.24.24.24

import com.maxmind.geoip.*;
import java.io.IOException;

class DistributedServiceTest {
    public static void main(String[] args) {
        try {
	    // /usr/local/share/GeoIP/GeoIPCity.dat is the location of a backup local copy of the database
            LookupService cl = new LookupService(0,args[0]);
            Location l = cl.getLocation(args[1]);
            System.out.println("countryCode: " + l.countryCode +
			       " countryName: " + l.countryName +
			       " region: " + l.region +
                               " city: " + l.city +
                               " postalCode: " + l.postalCode +
                               " latitude: " + l.latitude +
                               " longitude: " + l.longitude);
	    cl.close();
	    }
        catch (IOException e) {
            System.out.println("IO Exception");
        }

    }
}

