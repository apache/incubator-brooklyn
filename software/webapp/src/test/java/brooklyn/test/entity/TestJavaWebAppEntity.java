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
package brooklyn.test.entity;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.effector.EffectorAndBody;
import brooklyn.entity.java.VanillaJavaAppImpl;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.location.Location;
import brooklyn.util.flags.SetFromFlag;

/**
 * Mock web application server entity for testing.
 */
public class TestJavaWebAppEntity extends VanillaJavaAppImpl {
	private static final Logger LOG = LoggerFactory.getLogger(TestJavaWebAppEntity.class);
    public static final Effector<Void> START = new EffectorAndBody<Void>(SoftwareProcessImpl.START, new MethodEffector<Void>(TestJavaWebAppEntity.class, "customStart").getBody());

    @SetFromFlag public int a;
    @SetFromFlag public int b;
    @SetFromFlag public int c;

    public TestJavaWebAppEntity() {}
    public TestJavaWebAppEntity(@SuppressWarnings("rawtypes") Map flags, Entity parent) { super(flags, parent); }
    
	public void waitForHttpPort() { }

    
	public void customStart(Collection<? extends Location> loc) {
	    ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);
        LOG.trace("Starting {}", this);
        ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        setAttribute(Attributes.SERVICE_UP, true);
    }

    @Override
	protected void doStop() {
        LOG.trace("Stopping {}", this);
    }

    @Override
    public void doRestart() {
        throw new UnsupportedOperationException();
    }

	public synchronized void spoofRequest() {
		Integer rc = getAttribute(WebAppServiceConstants.REQUEST_COUNT);
		if (rc==null) rc = 0;
		setAttribute(WebAppServiceConstants.REQUEST_COUNT, rc+1);
	}
}
