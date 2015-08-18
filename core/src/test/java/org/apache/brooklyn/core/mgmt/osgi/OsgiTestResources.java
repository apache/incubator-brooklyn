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
package org.apache.brooklyn.core.mgmt.osgi;

/**
 * Many OSGi tests require OSGi bundles (of course). Test bundles have been collected here
 * for convenience and clarity. Available bundles (on the classpath, with source code
 * either embedded or in /src/dependencies) are described by the constants in this class.
 * <p>
 * Some of these bundles are also used in REST API tests, as that stretches catalog further
 * (using CAMP) and that is one area where OSGi is heavily used. 
 */
public class OsgiTestResources {


    /**
     * brooklyn-osgi-test-a_0.1.0 -
     * defines TestA which has a "times" method and a static multiplier field;
     * we set the multiplier to determine when we are sharing versions and when not
     */
    public static final String BROOKLYN_OSGI_TEST_A_0_1_0_PATH = "/brooklyn/osgi/brooklyn-osgi-test-a_0.1.0.jar";

    /**
     * brooklyn-test-osgi-entities (v 0.1.0) -
     * defines an entity and an application, to confirm it can be read and used by brooklyn
     */
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_PATH = "/brooklyn/osgi/brooklyn-test-osgi-entities.jar";
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_SIMPLE_APPLICATION = "org.apache.brooklyn.test.osgi.entities.SimpleApplication";
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_SIMPLE_ENTITY = "org.apache.brooklyn.test.osgi.entities.SimpleEntity";
    public static final String BROOKLYN_TEST_OSGI_ENTITIES_SIMPLE_POLICY = "org.apache.brooklyn.test.osgi.entities.SimplePolicy";

    /**
     * brooklyn-test-osgi-more-entities_0.1.0 -
     * another bundle with a minimal sayHi effector, used to test versioning and dependencies
     * (this one has no dependencies, but see also {@value #BROOKLYN_TEST_MORE_ENTITIES_V2_PATH})
     */
    public static final String BROOKLYN_TEST_MORE_ENTITIES_SYMBOLIC_NAME_FINAL_PART = "brooklyn-test-osgi-more-entities";
    public static final String BROOKLYN_TEST_MORE_ENTITIES_SYMBOLIC_NAME_FULL = 
        "org.apache.brooklyn.test.resources.osgi."+BROOKLYN_TEST_MORE_ENTITIES_SYMBOLIC_NAME_FINAL_PART;
    public static final String BROOKLYN_TEST_MORE_ENTITIES_V1_PATH = "/brooklyn/osgi/" + BROOKLYN_TEST_MORE_ENTITIES_SYMBOLIC_NAME_FINAL_PART + "_0.1.0.jar";
    public static final String BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY = "org.apache.brooklyn.test.osgi.entities.more.MoreEntity";
    
    /**
     * brooklyn-test-osgi-more-entities_0.2.0 -
     * similar to {@link #BROOKLYN_TEST_MORE_ENTITIES_V1_PATH} but saying "HI NAME" rather than "Hi NAME",
     * and declaring an explicit dependency on SimplePolicy from {@link #BROOKLYN_TEST_OSGI_ENTITIES_PATH}
     */
    public static final String BROOKLYN_TEST_MORE_ENTITIES_V2_PATH = "/brooklyn/osgi/" + BROOKLYN_TEST_MORE_ENTITIES_SYMBOLIC_NAME_FINAL_PART + "_0.2.0.jar";
    
    /**
     * bundle with identical metadata (same symbolic name and version -- hence being an evil twin) 
     * as {@link #BROOKLYN_TEST_MORE_ENTITIES_V2_PATH},
     * but slightly different behaviour -- saying "HO NAME" -- in order to make sure we can differentiate two two
     * at runtime.
     */
    public static final String BROOKLYN_TEST_MORE_ENTITIES_V2_EVIL_TWIN_PATH = "/brooklyn/osgi/" + BROOKLYN_TEST_MORE_ENTITIES_SYMBOLIC_NAME_FINAL_PART + "_evil-twin_0.2.0.jar";
    
}
