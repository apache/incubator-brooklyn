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
/* RegionLookupTest.java */

/* Requires subscription to MaxMind GeoIP Region database */

import com.maxmind.geoip.*;
import java.io.IOException;

class RegionLookupTest {
    public static void main(String[] args) {
        try {
            LookupService cl = new LookupService("/usr/local/share/GeoIP/GeoIPRegion.dat");
            Region l = cl.getRegion(args[0]);
            System.out.println("Country Code: " + l.countryCode);
            System.out.println("Country Name: " + l.countryName);
            System.out.println("Region Code: " + l.region);
            System.out.println("Region Name: " + regionName.regionNameByCode(l.countryCode,l.region));
            cl.close();
        }
        catch (IOException e) {
            System.out.println("IO Exception");
        }
    }
}
