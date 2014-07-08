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
/* NetspeedLookup.java */

import com.maxmind.geoip.*;
import java.io.IOException;

/* sample of how to use the GeoIP Java API with GeoIP Netspeed database */
/* Usage: java NetspeedLookupTest 24.24.24.24 */

class NetspeedLookupTest {
    public static void main(String[] args) {
        try {
            LookupService cl = new LookupService("/usr/local/share/GeoIP/GeoIPNetspeed.dat");
            if (args.length > 0) {
                int speed = cl.getID(args[0]);
	        if (speed == cl.GEOIP_UNKNOWN_SPEED){
		    System.out.println("Unknown");
                } else if (speed == cl.GEOIP_DIALUP_SPEED) {
		    System.out.println("Dialup");
                } else if (speed == cl.GEOIP_CABLEDSL_SPEED) {
		    System.out.println("Cable/DSL");
                } else if (speed == cl.GEOIP_CORPORATE_SPEED) {
		    System.out.println("Corporate");
                }
            } else {
 	        System.out.println("input a ip address\n");
 	    }
	}
        catch (IOException e) {
            System.out.println("IO Exception");
        }
    }
}
