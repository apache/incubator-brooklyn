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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.Lists;

/**
 * Mock entity for testing.
 */
public class TestEntityImpl extends AbstractEntity implements TestEntity {
    private static final Logger LOG = LoggerFactory.getLogger(TestEntityImpl.class);

	protected int sequenceValue = 0;
	protected AtomicInteger counter = new AtomicInteger(0);
	protected Map<?,?> constructorProperties;
	protected Map<?,?> configureProperties;
    protected List<String> callHistory = Collections.synchronizedList(Lists.<String>newArrayList());
    
    public TestEntityImpl() {
        super();
    }
    public TestEntityImpl(Map properties) {
        this(properties, null);
    }
    public TestEntityImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public TestEntityImpl(Map properties, Entity parent) {
        super(properties, parent);
        this.constructorProperties = properties;
    }
    
    public AbstractEntity configure(Map flags) {
        this.configureProperties = flags;
        return super.configure(flags);
    }
    
    @Override // made public for testing
    public boolean isLegacyConstruction() {
        return super.isLegacyConstruction();
    }
    
    @Override
    public void myEffector() {
        if (LOG.isTraceEnabled()) LOG.trace("In myEffector for {}", this);
        callHistory.add("myEffector");
    }
    
    @Override
    public Object identityEffector(Object arg) {
        if (LOG.isTraceEnabled()) LOG.trace("In identityEffector for {}", this);
        callHistory.add("identityEffector");
        return checkNotNull(arg, "arg");
    }
    
    @Override
    public AtomicInteger getCounter() {
        return counter;
    }
    
    @Override
    public int getCount() {
        return counter.get();
    }

    @Override
    public Map<?,?> getConstructorProperties() {
        return constructorProperties;
    }
    
    @Override
    public Map<?,?> getConfigureProperties() {
        return configureProperties;
    }
    
    @Override
    public synchronized int getSequenceValue() {
        return sequenceValue;
    }

    @Override
    public synchronized void setSequenceValue(int value) {
        sequenceValue = value;
        setAttribute(SEQUENCE, value);
    }

    @Override
    public void start(Collection<? extends Location> locs) {
        LOG.trace("Starting {}", this);
        callHistory.add("start");
        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);
        counter.incrementAndGet();
        addLocations(locs);
        ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        setAttribute(SERVICE_UP, true);
    }

    @Override
    public void stop() { 
        LOG.trace("Stopping {}", this);
        callHistory.add("stop");
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);
        counter.decrementAndGet();
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPED);
        setAttribute(SERVICE_UP, false);
    }

    @Override
    public void restart() {
        LOG.trace("Restarting {}", this);
        callHistory.add("restart");
    }
    
    /**
     * TODO Rename to addChild
     */
    @Override
    public <T extends Entity> T createChild(EntitySpec<T> spec) {
        return addChild(spec);
    }

    @Override
    public <T extends Entity> T createAndManageChild(EntitySpec<T> spec) {
        if (!getManagementSupport().isDeployed()) throw new IllegalStateException("Entity "+this+" not managed");
        T child = createChild(spec);
        getEntityManager().manage(child);
        return child;
    }

    @Override
    public Entity createAndManageChildFromConfig() {
        return createAndManageChild(checkNotNull(getConfig(CHILD_SPEC), "childSpec"));
    }

//    @Override
//    public String toString() {
//        String id = getId();
//        return getEntityType().getSimpleName()+"["+id.substring(Math.max(0, id.length()-8))+"]";
//    }

    @Override
    public List<String> getCallHistory() {
        return callHistory;
    }
}
