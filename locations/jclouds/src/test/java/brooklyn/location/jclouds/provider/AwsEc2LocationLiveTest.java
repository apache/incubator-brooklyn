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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class AwsEc2LocationLiveTest extends AbstractJcloudsLocationTest {

    private static final String PROVIDER = "aws-ec2";
    private static final String EUWEST_REGION_NAME = "eu-west-1";
    private static final String USEAST_REGION_NAME = "us-east-1";
    private static final String EUWEST_IMAGE_ID = EUWEST_REGION_NAME+"/"+"ami-89def4fd"; // RightImage_CentOS_5.4_i386_v5.5.9_EBS
    private static final String USEAST_IMAGE_ID = USEAST_REGION_NAME+"/"+"ami-2342a94a"; // RightImage_CentOS_5.4_i386_v5.5.9_EBS
    private static final String IMAGE_OWNER = "411009282317";
    private static final String IMAGE_PATTERN = "RightImage_CentOS_5.4_i386_v5.5.9_EBS";

    public AwsEc2LocationLiveTest() {
        super(PROVIDER);
    }

    @Override
    @DataProvider(name = "fromImageId")
    public Object[][] cloudAndImageIds() {
        return new Object[][] {
                new Object[] { EUWEST_REGION_NAME, EUWEST_IMAGE_ID, IMAGE_OWNER },
                new Object[] { USEAST_REGION_NAME, USEAST_IMAGE_ID, IMAGE_OWNER }
            };
    }

    @Override
    @DataProvider(name = "fromImageDescriptionPattern")
    public Object[][] cloudAndImageDescriptionPatterns() {
        return new Object[][] {
                new Object[] { EUWEST_REGION_NAME, IMAGE_PATTERN, IMAGE_OWNER },
                new Object[] { USEAST_REGION_NAME, IMAGE_PATTERN, IMAGE_OWNER }
            };
    }

    @Override
    @DataProvider(name = "fromImageNamePattern")
    public Object[][] cloudAndImageNamePatterns() {
        return new Object[][] {
                new Object[] { USEAST_REGION_NAME, IMAGE_PATTERN, IMAGE_OWNER }
            };
    }

    @Test(enabled = false)
    public void noop() { } /* just exists to let testNG IDE run the test */
}
