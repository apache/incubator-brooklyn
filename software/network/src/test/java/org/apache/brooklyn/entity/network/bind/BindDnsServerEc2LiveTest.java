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
package org.apache.brooklyn.entity.network.bind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.location.Location;

public class BindDnsServerEc2LiveTest extends AbstractEc2LiveTest {
    private static final Logger LOG = LoggerFactory.getLogger(BindDnsServerEc2LiveTest.class);

    protected void doTest(Location testLocation) throws Exception {
        BindDnsServerLiveTest.testBindStartsAndUpdates(app, testLocation);
    }

    @Override
    public void test_Red_Hat_Enterprise_Linux_6() throws Exception {
        super.test_Red_Hat_Enterprise_Linux_6();
    }

    @Override
    public void test_CentOS_6_3() throws Exception {
        super.test_CentOS_6_3();
    }

    @Override
    public void test_Debian_7_2() throws Exception {
        super.test_Debian_7_2();
    }

    @Override
    public void test_Debian_6() throws Exception {
        LOG.debug("{} skipped Debian 6 test", this);
    }

    @Override
    public void test_Ubuntu_10_0() throws Exception {
        LOG.debug("{} skipped Ubuntu 10 test", this);
    }

    @Override
    public void test_CentOS_5() throws Exception {
        LOG.debug("{} skipped CentOS 5.6 test", this);
    }
}
