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
package brooklyn.util.internal;

/** 
 * Convenience for retrieving well-defined system properties, including checking if they have been set etc.
 */
public class BrooklynSystemProperties {

    // TODO should these become ConfigKeys ?
    
    public static BooleanSystemProperty DEBUG = new BooleanSystemProperty("brooklyn.debug");
    public static BooleanSystemProperty EXPERIMENTAL = new BooleanSystemProperty("brooklyn.experimental");
    
    /** controls how long jsch delays between commands it issues */
    // -Dbrooklyn.jsch.exec.delay=100
    public static IntegerSystemProperty JSCH_EXEC_DELAY = new IntegerSystemProperty("brooklyn.jsch.exec.delay");

    /** allows specifying a particular geo lookup service (to lookup IP addresses), as the class FQN to use */
    // -Dbrooklyn.location.geo.HostGeoLookup=brooklyn.location.geo.UtraceHostGeoLookup
    public static StringSystemProperty HOST_GEO_LOOKUP_IMPL = new StringSystemProperty("brooklyn.location.geo.HostGeoLookup");

}
