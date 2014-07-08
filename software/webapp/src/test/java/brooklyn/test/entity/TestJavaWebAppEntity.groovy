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
package brooklyn.test.entity

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.basic.SoftwareProcessImpl
import brooklyn.entity.effector.EffectorAndBody
import brooklyn.entity.java.VanillaJavaAppImpl
import brooklyn.entity.webapp.WebAppServiceConstants
import brooklyn.location.Location
import brooklyn.util.flags.SetFromFlag

/**
 * Mock web application server entity for testing.
 */
public class TestJavaWebAppEntity extends VanillaJavaAppImpl {
	private static final Logger LOG = LoggerFactory.getLogger(TestJavaWebAppEntity.class);
    public static final Effector<Void> START = new EffectorAndBody<Void>(SoftwareProcessImpl.START, new MethodEffector(TestJavaWebAppEntity.class, "customStart").getBody());

    public TestJavaWebAppEntity(Map properties=[:], Entity parent=null) {
        super(properties, parent)
    }
    
    @SetFromFlag public int a;
    @SetFromFlag public int b;
    @SetFromFlag public int c;

	public void waitForHttpPort() { }

    
	public void customStart(Collection<? extends Location> loc) {
        LOG.trace "Starting {}", this
    }

    @Override
	protected void doStop() {
        LOG.trace "Stopping {}", this
    }

    @Override
    public void doRestart() {
        throw new UnsupportedOperationException();
    }

	@Override
    String toString() {
        return "Entity["+id[-8..-1]+"]"
    }

	public synchronized void spoofRequest() {
		def rc = getAttribute(WebAppServiceConstants.REQUEST_COUNT) ?: 0
		setAttribute(WebAppServiceConstants.REQUEST_COUNT, rc+1)
	}
}
