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
package brooklyn.location.jclouds.provider;

import org.testng.annotations.DataProvider
import org.testng.annotations.Test

//FIXME get Greenhouse working

class GreenhouseDataLocationLiveTest extends AbstractJcloudsLocationTest {
    
    private static final String PROVIDER = "greenhousedata-element-vcloud"
    private static final String REGION_NAME = null
    private static final String IMAGE_ID = "1"
    private static final String IMAGE_OWNER = null
    private static final String IMAGE_PATTERN = ".*Ubuntu_Server_x64.*"

    public GreenhouseDataLocationLiveTest() {
        super(PROVIDER)
    }

    @Override
    @DataProvider(name = "fromImageId")
    public Object[][] cloudAndImageIds() {
        return [ [ REGION_NAME, IMAGE_ID, IMAGE_OWNER ] ]
    }

    @Override
    @DataProvider(name = "fromImageDescriptionPattern")
    public Object[][] cloudAndImageDescriptionPatterns() {
        return [ [ REGION_NAME, IMAGE_PATTERN, IMAGE_OWNER ] ]
    }

    @Override
    @DataProvider(name = "fromImageNamePattern")
    public Object[][] cloudAndImageNamePatterns() {
        return []
    }

    @Test(enabled = false)
    public void noop() { /* just exists to let testNG IDE run the test */ }
    
}
