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
package brooklyn.management.osgi;

/**
* Many OSGi tests require OSGi bundles (of course). Test bundles have been collected here
* for convenience and clarity. Available bundles (on the classpath, with source code
* either embedded or in /src/dependencies) are:
* <p>
* <li>brooklyn-osgi-test-a_0.1.0 -
*     defines TestA which has a "times" method and a static multiplier field;
*     we set the multiplier to determine when we are sharing versions and when not
*     
* <li>brooklyn-test-osgi-entities (also version 0.1.0) -
*     defines an entity and an application, to confirm it can be read and used by brooklyn
* <p>
* Some of these bundles are also used in REST API tests, as that stretches catalog further
* (using CAMP) and that is one area where OSGi is heavily used. 
*/
public class OsgiTestResources {

    public static final String BROOKLYN_OSGI_TEST_A_0_1_0_URL = "classpath:/brooklyn/osgi/brooklyn-osgi-test-a_0.1.0.jar";

    public static final String BROOKLYN_TEST_OSGI_ENTITIES_PATH = "/brooklyn/osgi/brooklyn-test-osgi-entities.jar";
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_URL = "classpath:"+BROOKLYN_TEST_OSGI_ENTITIES_PATH;

}
