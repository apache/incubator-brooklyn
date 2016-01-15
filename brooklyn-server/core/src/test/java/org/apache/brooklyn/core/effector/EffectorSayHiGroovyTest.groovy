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
package org.apache.brooklyn.core.effector

import static org.testng.Assert.*

import org.apache.brooklyn.api.effector.Effector
import org.apache.brooklyn.api.entity.Entity
import org.apache.brooklyn.api.entity.EntitySpec
import org.apache.brooklyn.api.entity.ImplementedBy
import org.apache.brooklyn.api.mgmt.ManagementContext
import org.apache.brooklyn.api.mgmt.Task
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.internal.EffectorUtils
import org.apache.brooklyn.core.test.entity.TestApplication
import org.apache.brooklyn.core.annotation.EffectorParam
import org.apache.brooklyn.core.effector.BasicParameterType;
import org.apache.brooklyn.core.effector.ExplicitEffector;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.entity.AbstractEntity
import org.apache.brooklyn.core.entity.Entities
import org.apache.brooklyn.core.entity.trait.Startable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

/**
 * Test the operation of the {@link Effector} implementations.
 *
 * TODO delete? the groovy causes compile errors, and EffectorSayHiTest does most of what this does
 */
public class EffectorSayHiGroovyTest {
    private static final Logger log = LoggerFactory.getLogger(EffectorSayHiTest.class);

    private TestApplication app;
    private MyEntity e;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = TestApplication.Factory.newManagedInstanceForTests();
        e = app.createAndManageChild(EntitySpec.create(MyEntity.class));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testFindEffectors() {
        assertEquals("sayHi1", e.SAY_HI_1.getName());
        assertEquals(["name", "greeting"], e.SAY_HI_1.getParameters()[0..1]*.getName());
        assertEquals("says hello", e.SAY_HI_1.getDescription());

		assertEquals("sayHi1", e.SAY_HI_1_ALT.getName());
		assertEquals(["name", "greeting"], e.SAY_HI_1_ALT.getParameters()[0..1]*.getName());
		assertEquals("says hello", e.SAY_HI_1_ALT.getDescription());

		assertEquals("sayHi2", e.SAY_HI_2.getName());
		assertEquals(["name", "greeting"], e.SAY_HI_2.getParameters()[0..1]*.getName());
		assertEquals("says hello", e.SAY_HI_2.getDescription());
    }

    @Test
    public void testFindTraitEffectors() {
        assertEquals("locations", Startable.START.getParameters()[0].getName());
    }

    @Test
    public void testInvokeEffectorMethod1BypassInterception() {
        String name = "sayHi1"
        def args = ["Bob", "hello"] as Object[]

        //try the alt syntax recommended from web
        def metaMethod = e.metaClass.getMetaMethod(name, args)
        if (metaMethod==null)
            throw new IllegalArgumentException("Invalid arguments (no method found) for method $name: "+args);
        assertEquals("hello Bob", metaMethod.invoke(e, args))
    }

    @Test
    public void testInvokeEffectorMethod2BypassInterception() {
        String name = "sayHi2"
        def args = ["Bob", "hello"] as Object[]
        assertEquals("hello Bob", e.metaClass.invokeMethod(e, name, args))
    }

    @Test
    public void testInvokeEffectors1() {
        assertEquals("hi Bob", e.sayHi1("Bob", "hi"))

        assertEquals("hello Bob", e.SAY_HI_1.call(e, [name:"Bob"]) )
        assertEquals("hello Bob", e.invoke(e.SAY_HI_1, [name:"Bob"]).get() );

		assertEquals("hello Bob", e.SAY_HI_1_ALT.call(e, [name:"Bob"]) )
    }

    @Test
    public void testInvokeEffectors2() {
        assertEquals("hi Bob", e.sayHi2("Bob", "hi"))

        assertEquals("hello Bob", e.SAY_HI_2.call(e, [name:"Bob"]) )
        assertEquals("hello Bob", e.invoke(e.SAY_HI_2, [name:"Bob"]).get() );
        
    }

    @Test
    public void testCanRetrieveTaskForEffector() {
        e.sayHi2("Bob", "hi")

        ManagementContext managementContext = e.getManagementContext()

        Set<Task> tasks = managementContext.getExecutionManager().getTasksWithAllTags([
            BrooklynTaskTags.tagForContextEntity(e),"EFFECTOR"])
        assertEquals(tasks.size(), 1)
        assertTrue(tasks.iterator().next().getDescription().contains("sayHi2"))
    }
}
public interface CanSayHi {
	//prefer following simple groovy syntax
	static Effector<String> SAY_HI_1 = new MethodEffector<String>(CanSayHi.&sayHi1);
	//slightly longer-winded pojo also supported
	static Effector<String> SAY_HI_1_ALT = new MethodEffector<String>(CanSayHi.class, "sayHi1");

	@org.apache.brooklyn.core.annotation.Effector(description="says hello")
	public String sayHi1(
		@EffectorParam(name="name") String name,
		@EffectorParam(name="greeting", defaultValue="hello", description="what to say") String greeting);

	//finally there is a way to provide a class/closure if needed or preferred for some odd reason
	static Effector<String> SAY_HI_2 =

		//groovy 1.8.2 balks at runtime during getCallSiteArray (bug 5122) if we use anonymous inner class
//	  new ExplicitEffector<CanSayHi,String>(
//			"sayHi2", String.class, [
//					[ "name", String.class, "person to say hi to" ] as BasicParameterType<String>,
//					[ "greeting", String.class, "what to say as greeting", "hello" ] as BasicParameterType<String>
//				],
//			"says hello to a person") {
//		public String invokeEffector(CanSayHi e, Map m) {
//			e.sayHi2(m)
//		}
//	};
	//following is a workaround, not greatly enamoured of it... but MethodEffector is generally preferred anyway
		ExplicitEffector.create("sayHi2", String.class, [
					new BasicParameterType<String>("name", String.class, "person to say hi to"),
					new BasicParameterType<String>("greeting", String.class, "what to say as greeting", "hello")
				],
			"says hello", { e, m ->
                def args = EffectorUtils.prepareArgsForEffector(SAY_HI_2, m);
                e.sayHi2(args[0], args[1]) })

	public String sayHi2(String name, String greeting);

}

@ImplementedBy(MyEntityImpl.class)
public interface MyEntity extends Entity, CanSayHi {
}

public class MyEntityImpl extends AbstractEntity implements MyEntity {
    public String sayHi1(String name, String greeting) { "$greeting $name" }
	public String sayHi2(String name, String greeting) { "$greeting $name" }
}
