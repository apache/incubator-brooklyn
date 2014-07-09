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

import static org.testng.Assert.*

import org.testng.annotations.DataProvider

public class GoGridLocationLiveTest extends AbstractJcloudsLocationTest {
    
    private static final String PROVIDER = "gogrid"
    private static final String USWEST_REGION_NAME = "1"//"us-west-1"
    private static final String USWEST_IMAGE_ID = "1532"
    private static final String IMAGE_NAME_PATTERN = "CentOS 5.3 (64-bit) w/ None"
    private static final String IMAGE_OWNER = null
    
    public GoGridLocationLiveTest() {
        super(PROVIDER)
    }
    
    @Override
    @DataProvider(name = "fromImageId")
    public Object[][] cloudAndImageIds() {
        return [ [USWEST_REGION_NAME, USWEST_IMAGE_ID, IMAGE_OWNER] ]
    }

    @Override
    @DataProvider(name = "fromImageNamePattern")
    public Object[][] cloudAndImageNamePatterns() {
        return [ [USWEST_REGION_NAME, IMAGE_NAME_PATTERN, IMAGE_OWNER] ]
    }
    
    @Override
    @DataProvider(name = "fromImageDescriptionPattern")
    public Object[][] cloudAndImageDescriptionPatterns() {
        return []
    }
}
