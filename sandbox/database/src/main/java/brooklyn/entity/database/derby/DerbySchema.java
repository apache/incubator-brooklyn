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
package brooklyn.entity.database.derby;

import static java.lang.String.format;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.ObjectName;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.database.Schema;
import brooklyn.entity.java.UsesJmx;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Objects.ToStringHelper;

public class DerbySchema extends AbstractEntity implements Schema {

    // FIXME Needs reviewed and implemented properly; while fixing compilation errors
    // I added enough for it to look mostly plausible but it's completely untested.
    // And I have not looked up the derby docs to check that the attributes etc are valid. 
    
    // TODO Somehow share jmx connection with DerbyDatabase instance
    
    // TODO Declare effectors
    
    public static AttributeSensor<Integer> SCHEMA_DEPTH = new BasicAttributeSensor<Integer>(
            Integer.class, "derby.schema.depth", "schema depth");
    
    public static AttributeSensor<Integer> MESSAGE_COUNT = new BasicAttributeSensor<Integer>(
            Integer.class, "derby.schema.messageCount", "message count");
    
    @SetFromFlag(defaultVal="localhost")
    String virtualHost;
    
    @SetFromFlag(nullable=false)
    String name;

    protected ObjectName virtualHostManager;
    protected ObjectName exchange;

    transient JmxHelper jmxHelper;
    transient JmxFeed jmxFeed;
    
    public DerbySchema() {
        super(MutableMap.of(), null);
    }
    public DerbySchema(Map properties) {
        super(properties, null);
    }
    public DerbySchema(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public DerbySchema(Map properties, Entity parent) {
        super(properties, parent);
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public DerbyDatabase getParent() {
        return (DerbyDatabase) super.getParent();
    }
    
    /**
     * Return the JDBC connection URL for the schema.
     */
    public String getConnectionUrl() { return String.format("jdbc:derby:%s", name); }

    public void init() {
        try {
            virtualHostManager = new ObjectName(format("org.apache.derby:type=VirtualHost.VirtualHostManager,VirtualHost=\"%s\"", virtualHost));
            exchange = new ObjectName(format("org.apache.derby:type=VirtualHost.Exchange,VirtualHost=\"%s\",name=\"amq.direct\",ExchangeType=direct", virtualHost));
            create();

            jmxHelper = new JmxHelper((EntityLocal)getParent());

            ObjectName schemaMBeanName = new ObjectName(format("org.apache.derby:type=VirtualHost.Schema,VirtualHost=\"%s\",name=\"%s\"", virtualHost, name));

            jmxFeed = JmxFeed.builder()
                    .entity(this)
                    .helper(jmxHelper)
                    .period(500, TimeUnit.MILLISECONDS)
                    .pollAttribute(new JmxAttributePollConfig<Integer>(SCHEMA_DEPTH)
                            .objectName(schemaMBeanName)
                            .attributeName("SchemaDepth"))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(MESSAGE_COUNT)
                            .objectName(schemaMBeanName)
                            .attributeName("MessageCount"))
                    .build();
            
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public void create() {
        jmxHelper.operation(virtualHostManager, "createNewSchema", name, getParent().getAttribute(UsesJmx.JMX_USER), true);
        jmxHelper.operation(exchange, "createNewBinding", name, name);
    }

    public void remove() {
        jmxHelper.operation(exchange, "removeBinding", name, name);
        jmxHelper.operation(virtualHostManager, "deleteSchema", name);
    }

    @Override
    public void destroy() {
        if (jmxFeed != null) jmxFeed.stop();
        super.destroy();
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("name", name);
    }
}
