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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.internal.EntityLocal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

public class TestBindDnsServerImpl extends BindDnsServerImpl {

    private static final Logger LOG = LoggerFactory.getLogger(TestBindDnsServerImpl.class);

    public static class TestBindDnsServerDriver extends DoNothingSoftwareProcessDriver implements BindDnsServerDriver {

        private final BindDnsServerSshDriver delegate;

        public TestBindDnsServerDriver(EntityLocal entity, SshMachineLocation machine) {
            super(entity, machine);
            delegate = new BindDnsServerSshDriver((BindDnsServerImpl) entity, machine);
        }

        @Override
        public void updateBindConfiguration() {
            LOG.info("Skipped copy of Bind configuration files to server");
            LOG.debug("Configuration:\n{}", processTemplate(entity.getConfig(BindDnsServer.NAMED_CONF_TEMPLATE)));
            LOG.debug("domain.zone:\n{}", processTemplate(entity.getConfig(BindDnsServer.DOMAIN_ZONE_FILE_TEMPLATE)));
            LOG.debug("reverse.zone:\n{}", processTemplate(entity.getConfig(BindDnsServer.REVERSE_ZONE_FILE_TEMPLATE)));
        }

        @Override
        public BindOsSupport getOsSupport() {
            return BindOsSupport.forRhel();
        }

        public String getDataDirectory() {
            return delegate.getDataDirectory();
        }

        public String getDynamicDirectory() {
            return delegate.getDynamicDirectory();
        }

        public String getDomainZoneFile() {
            return delegate.getDomainZoneFile();
        }

        public String getReverseZoneFile() {
            return delegate.getReverseZoneFile();
        }

        public String getRfc1912ZonesFile() {
            return delegate.getRfc1912ZonesFile();
        }
    }

    @Override
    public Class<?> getDriverInterface() {
        return TestBindDnsServerDriver.class;
    }

    @Override
    protected void configureResolver(Entity entity) {
        LOG.debug("Skipped configuration of resolver on {}", entity);
    }

    @Override
    protected void appendTemplate(String template, String destination, SshMachineLocation machine) {
        LOG.debug("Skipped append of template to {}@{}", destination, machine);
    }

}
