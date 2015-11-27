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
package org.apache.brooklyn.entity.database.rubyrep;

import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import org.apache.brooklyn.entity.database.VogellaExampleAccess;
import org.apache.brooklyn.entity.database.mysql.MySqlIntegrationTest;
import org.apache.brooklyn.entity.database.mysql.MySqlNode;
import org.apache.brooklyn.entity.database.postgresql.PostgreSqlIntegrationTest;
import org.apache.brooklyn.entity.database.postgresql.PostgreSqlNode;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

public class RubyRepIntegrationTest {

    public static final Logger log = LoggerFactory.getLogger(RubyRepIntegrationTest.class);
    protected BrooklynProperties brooklynProperties;
    protected ManagementContext managementContext;
    protected TestApplication tapp;
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        brooklynProperties = BrooklynProperties.Factory.newDefault();
        managementContext = new LocalManagementContext(brooklynProperties);
        tapp = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        Entities.destroyAllCatching(managementContext);
    }

    /*
        Exception org.apache.brooklyn.util.exceptions.PropagatedRuntimeException
        
        Message: (none)
        Stacktrace:
        
        at org.apache.brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:103)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:353)
        at org.apache.brooklyn.util.core.task.DynamicTasks$TaskQueueingResult.andWaitForSuccess(DynamicTasks.java:159)
        at org.apache.brooklyn.core.objs.proxy.EntityProxyImpl.invoke(EntityProxyImpl.java:211)
        at com.sun.proxy.$Proxy49.start(Unknown Source)
        at org.apache.brooklyn.entity.database.rubyrep.RubyRepIntegrationTest.startInLocation(RubyRepIntegrationTest.java:139)
        at org.apache.brooklyn.entity.database.rubyrep.RubyRepIntegrationTest.startInLocation(RubyRepIntegrationTest.java:119)
        at org.apache.brooklyn.entity.database.rubyrep.RubyRepIntegrationTest.test_localhost_mysql(RubyRepIntegrationTest.java:80)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:606)
        at org.testng.internal.MethodInvocationHelper.invokeMethod(MethodInvocationHelper.java:84)
        at org.testng.internal.Invoker.invokeMethod(Invoker.java:714)
        at org.testng.internal.Invoker.invokeTestMethod(Invoker.java:901)
        at org.testng.internal.Invoker.invokeTestMethods(Invoker.java:1231)
        at org.testng.internal.TestMethodWorker.invokeTestMethods(TestMethodWorker.java:127)
        at org.testng.internal.TestMethodWorker.run(TestMethodWorker.java:111)
        at org.testng.TestRunner.privateRun(TestRunner.java:767)
        at org.testng.TestRunner.run(TestRunner.java:617)
        at org.testng.SuiteRunner.runTest(SuiteRunner.java:348)
        at org.testng.SuiteRunner.runSequentially(SuiteRunner.java:343)
        at org.testng.SuiteRunner.privateRun(SuiteRunner.java:305)
        at org.testng.SuiteRunner.run(SuiteRunner.java:254)
        at org.testng.SuiteRunnerWorker.runSuite(SuiteRunnerWorker.java:52)
        at org.testng.SuiteRunnerWorker.run(SuiteRunnerWorker.java:86)
        at org.testng.TestNG.runSuitesSequentially(TestNG.java:1224)
        at org.testng.TestNG.runSuitesLocally(TestNG.java:1149)
        at org.testng.TestNG.run(TestNG.java:1057)
        at org.apache.maven.surefire.testng.TestNGExecutor.run(TestNGExecutor.java:115)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.executeMulti(TestNGDirectoryTestSuite.java:205)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.execute(TestNGDirectoryTestSuite.java:108)
        at org.apache.maven.surefire.testng.TestNGProvider.invoke(TestNGProvider.java:111)
        at org.apache.maven.surefire.booter.ForkedBooter.invokeProviderInSameClassLoader(ForkedBooter.java:203)
        at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:155)
        at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:103)
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at Application[r1CKCG7x]: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: 1 of 3 parallel child tasks failed: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=pePPOzBw}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=pePPOzBw} did not pass is-running check within the required 2m limit (2m 2s elapsed)
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:351)
        ... 34 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at Application[r1CKCG7x]: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: 1 of 3 parallel child tasks failed: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=pePPOzBw}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=pePPOzBw} did not pass is-running check within the required 2m limit (2m 2s elapsed)
        at org.apache.brooklyn.core.mgmt.internal.EffectorUtils.handleEffectorException(EffectorUtils.java:270)
        at org.apache.brooklyn.core.mgmt.internal.EffectorUtils.invokeMethodEffector(EffectorUtils.java:255)
        at org.apache.brooklyn.core.effector.MethodEffector.call(MethodEffector.java:149)
        at org.apache.brooklyn.core.entity.trait.Startable$StartEffectorBody.call(Startable.java:56)
        at org.apache.brooklyn.core.entity.trait.Startable$StartEffectorBody.call(Startable.java:50)
        at org.apache.brooklyn.core.effector.EffectorTasks$EffectorBodyTaskFactory$1.call(EffectorTasks.java:82)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask$DstJob.call(DynamicSequentialTask.java:342)
        at org.apache.brooklyn.util.core.task.BasicExecutionManager$SubmissionCallable.call(BasicExecutionManager.java:468)
        at java.util.concurrent.FutureTask.run(FutureTask.java:262)
        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:615)
        at java.lang.Thread.run(Thread.java:745)
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: 1 of 3 parallel child tasks failed: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=pePPOzBw}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=pePPOzBw} did not pass is-running check within the required 2m limit (2m 2s elapsed)
        at org.apache.brooklyn.core.mgmt.internal.AbstractManagementContext.invokeEffectorMethodSync(AbstractManagementContext.java:332)
        at org.apache.brooklyn.core.mgmt.internal.EffectorUtils.invokeMethodEffector(EffectorUtils.java:250)
        ... 10 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: 1 of 3 parallel child tasks failed: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=pePPOzBw}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=pePPOzBw} did not pass is-running check within the required 2m limit (2m 2s elapsed)
        at org.apache.brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:103)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:353)
        at org.apache.brooklyn.core.entity.trait.StartableMethods.start(StartableMethods.java:53)
        at org.apache.brooklyn.core.entity.AbstractApplication.doStart(AbstractApplication.java:178)
        at org.apache.brooklyn.core.entity.AbstractApplication.start(AbstractApplication.java:155)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:606)
        at org.codehaus.groovy.reflection.CachedMethod.invoke(CachedMethod.java:90)
        at groovy.lang.MetaMethod.doMethodInvoke(MetaMethod.java:233)
        at groovy.lang.MetaClassImpl.invokeMethod(MetaClassImpl.java:1085)
        at groovy.lang.MetaClassImpl.invokeMethod(MetaClassImpl.java:909)
        at groovy.lang.DelegatingMetaClass.invokeMethod(DelegatingMetaClass.java:149)
        at groovy.lang.MetaObjectProtocol$invokeMethod.call(Unknown Source)
        at org.codehaus.groovy.runtime.callsite.CallSiteArray.defaultCall(CallSiteArray.java:45)
        at groovy.lang.MetaObjectProtocol$invokeMethod.call(Unknown Source)
        at org.apache.brooklyn.util.groovy.GroovyJavaMethods.invokeMethodOnMetaClass(GroovyJavaMethods.java:191)
        at org.apache.brooklyn.core.mgmt.internal.AbstractManagementContext.invokeEffectorMethodLocal(AbstractManagementContext.java:304)
        at org.apache.brooklyn.core.mgmt.internal.AbstractManagementContext.invokeEffectorMethodSync(AbstractManagementContext.java:328)
        ... 11 more
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: 1 of 3 parallel child tasks failed: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=pePPOzBw}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=pePPOzBw} did not pass is-running check within the required 2m limit (2m 2s elapsed)
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:351)
        ... 29 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: 1 of 3 parallel child tasks failed: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=pePPOzBw}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=pePPOzBw} did not pass is-running check within the required 2m limit (2m 2s elapsed)
        at org.apache.brooklyn.util.exceptions.Exceptions.create(Exceptions.java:299)
        at org.apache.brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:287)
        at org.apache.brooklyn.util.core.task.ParallelTask.runJobs(ParallelTask.java:81)
        at org.apache.brooklyn.util.core.task.CompoundTask$1.call(CompoundTask.java:81)
        at org.apache.brooklyn.util.core.task.CompoundTask$1.call(CompoundTask.java:79)
        ... 5 more
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=pePPOzBw}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=pePPOzBw} did not pass is-running check within the required 2m limit (2m 2s elapsed)
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.ParallelTask.runJobs(ParallelTask.java:63)
        ... 7 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=pePPOzBw}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=pePPOzBw} did not pass is-running check within the required 2m limit (2m 2s elapsed)
        at org.apache.brooklyn.core.mgmt.internal.EffectorUtils.handleEffectorException(EffectorUtils.java:270)
        at org.apache.brooklyn.core.effector.EffectorTasks$EffectorBodyTaskFactory$2.handleException(EffectorTasks.java:90)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask.handleException(DynamicSequentialTask.java:452)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask$DstJob.call(DynamicSequentialTask.java:400)
        ... 5 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=pePPOzBw} did not pass is-running check within the required 2m limit (2m 2s elapsed)
        at org.apache.brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:103)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:353)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask.drain(DynamicSequentialTask.java:475)
        at org.apache.brooklyn.util.core.task.DynamicTasks.drain(DynamicTasks.java:313)
        at org.apache.brooklyn.util.core.task.DynamicTasks.waitForLast(DynamicTasks.java:302)
        at org.apache.brooklyn.entity.software.base.lifecycle.MachineLifecycleEffectorTasks.start(MachineLifecycleEffectorTasks.java:314)
        at org.apache.brooklyn.entity.software.base.lifecycle.MachineLifecycleEffectorTasks$StartEffectorBody.call(MachineLifecycleEffectorTasks.java:214)
        at org.apache.brooklyn.entity.software.base.lifecycle.MachineLifecycleEffectorTasks$StartEffectorBody.call(MachineLifecycleEffectorTasks.java:201)
        at org.apache.brooklyn.core.effector.EffectorTasks$EffectorBodyTaskFactory$1.call(EffectorTasks.java:82)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask$DstJob.call(DynamicSequentialTask.java:342)
        ... 5 more
        Caused by: java.util.concurrent.ExecutionException: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=pePPOzBw} did not pass is-running check within the required 2m limit (2m 2s elapsed)
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:351)
        ... 13 more
        Caused by: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=pePPOzBw} did not pass is-running check within the required 2m limit (2m 2s elapsed)
        at org.apache.brooklyn.entity.software.base.SoftwareProcessImpl.waitForEntityStart(SoftwareProcessImpl.java:592)
        at org.apache.brooklyn.entity.software.base.SoftwareProcessImpl.postDriverStart(SoftwareProcessImpl.java:266)
        at org.apache.brooklyn.entity.software.base.SoftwareProcessDriverLifecycleEffectorTasks.postStartCustom(SoftwareProcessDriverLifecycleEffectorTasks.java:169)
        at org.apache.brooklyn.entity.software.base.lifecycle.MachineLifecycleEffectorTasks$PostStartTask.run(MachineLifecycleEffectorTasks.java:572)
        at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:471)
        ... 6 more
     */
    @Test(groups = {"Integration","Broken"})
    public void test_localhost_mysql() throws Exception {
        MySqlNode db1 = tapp.createAndManageChild(EntitySpec.create(MySqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, MySqlIntegrationTest.CREATION_SCRIPT)
                .configure("test.table.name", "COMMENTS")
                .configure(MySqlNode.MYSQL_PORT, PortRanges.fromInteger(9111)));

        MySqlNode db2 = tapp.createAndManageChild(EntitySpec.create(MySqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, MySqlIntegrationTest.CREATION_SCRIPT)
                .configure("test.table.name", "COMMENTS")
                .configure(MySqlNode.MYSQL_PORT, PortRanges.fromInteger(9112)));


        startInLocation(tapp, db1, db2, new LocalhostMachineProvisioningLocation());
        testReplication(db1, db2);
    }

    /*
        Exception org.apache.brooklyn.util.exceptions.PropagatedRuntimeException
        
        Message: (none)
        Stacktrace:
        
        at org.apache.brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:103)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:353)
        at org.apache.brooklyn.util.core.task.DynamicTasks$TaskQueueingResult.andWaitForSuccess(DynamicTasks.java:159)
        at org.apache.brooklyn.core.objs.proxy.EntityProxyImpl.invoke(EntityProxyImpl.java:211)
        at com.sun.proxy.$Proxy53.start(Unknown Source)
        at org.apache.brooklyn.entity.database.rubyrep.RubyRepIntegrationTest.startInLocation(RubyRepIntegrationTest.java:139)
        at org.apache.brooklyn.entity.database.rubyrep.RubyRepIntegrationTest.test_localhost_postgres(RubyRepIntegrationTest.java:98)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:606)
        at org.testng.internal.MethodInvocationHelper.invokeMethod(MethodInvocationHelper.java:84)
        at org.testng.internal.Invoker.invokeMethod(Invoker.java:714)
        at org.testng.internal.Invoker.invokeTestMethod(Invoker.java:901)
        at org.testng.internal.Invoker.invokeTestMethods(Invoker.java:1231)
        at org.testng.internal.TestMethodWorker.invokeTestMethods(TestMethodWorker.java:127)
        at org.testng.internal.TestMethodWorker.run(TestMethodWorker.java:111)
        at org.testng.TestRunner.privateRun(TestRunner.java:767)
        at org.testng.TestRunner.run(TestRunner.java:617)
        at org.testng.SuiteRunner.runTest(SuiteRunner.java:348)
        at org.testng.SuiteRunner.runSequentially(SuiteRunner.java:343)
        at org.testng.SuiteRunner.privateRun(SuiteRunner.java:305)
        at org.testng.SuiteRunner.run(SuiteRunner.java:254)
        at org.testng.SuiteRunnerWorker.runSuite(SuiteRunnerWorker.java:52)
        at org.testng.SuiteRunnerWorker.run(SuiteRunnerWorker.java:86)
        at org.testng.TestNG.runSuitesSequentially(TestNG.java:1224)
        at org.testng.TestNG.runSuitesLocally(TestNG.java:1149)
        at org.testng.TestNG.run(TestNG.java:1057)
        at org.apache.maven.surefire.testng.TestNGExecutor.run(TestNGExecutor.java:115)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.executeMulti(TestNGDirectoryTestSuite.java:205)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.execute(TestNGDirectoryTestSuite.java:108)
        at org.apache.maven.surefire.testng.TestNGProvider.invoke(TestNGProvider.java:111)
        at org.apache.maven.surefire.booter.ForkedBooter.invokeProviderInSameClassLoader(ForkedBooter.java:203)
        at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:155)
        at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:103)
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at Application[OTSda29h]: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: 1 of 2 parallel child tasks failed: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=VhsCCM9k}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=VhsCCM9k} did not pass is-running check within the required 2m limit (2m 1s elapsed)
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:351)
        ... 33 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at Application[OTSda29h]: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: 1 of 2 parallel child tasks failed: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=VhsCCM9k}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=VhsCCM9k} did not pass is-running check within the required 2m limit (2m 1s elapsed)
        at org.apache.brooklyn.core.mgmt.internal.EffectorUtils.handleEffectorException(EffectorUtils.java:270)
        at org.apache.brooklyn.core.mgmt.internal.EffectorUtils.invokeMethodEffector(EffectorUtils.java:255)
        at org.apache.brooklyn.core.effector.MethodEffector.call(MethodEffector.java:149)
        at org.apache.brooklyn.core.entity.trait.Startable$StartEffectorBody.call(Startable.java:56)
        at org.apache.brooklyn.core.entity.trait.Startable$StartEffectorBody.call(Startable.java:50)
        at org.apache.brooklyn.core.effector.EffectorTasks$EffectorBodyTaskFactory$1.call(EffectorTasks.java:82)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask$DstJob.call(DynamicSequentialTask.java:342)
        at org.apache.brooklyn.util.core.task.BasicExecutionManager$SubmissionCallable.call(BasicExecutionManager.java:468)
        at java.util.concurrent.FutureTask.run(FutureTask.java:262)
        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1145)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:615)
        at java.lang.Thread.run(Thread.java:745)
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: 1 of 2 parallel child tasks failed: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=VhsCCM9k}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=VhsCCM9k} did not pass is-running check within the required 2m limit (2m 1s elapsed)
        at org.apache.brooklyn.core.mgmt.internal.AbstractManagementContext.invokeEffectorMethodSync(AbstractManagementContext.java:332)
        at org.apache.brooklyn.core.mgmt.internal.EffectorUtils.invokeMethodEffector(EffectorUtils.java:250)
        ... 10 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: 1 of 2 parallel child tasks failed: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=VhsCCM9k}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=VhsCCM9k} did not pass is-running check within the required 2m limit (2m 1s elapsed)
        at org.apache.brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:103)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:353)
        at org.apache.brooklyn.core.entity.trait.StartableMethods.start(StartableMethods.java:53)
        at org.apache.brooklyn.core.entity.AbstractApplication.doStart(AbstractApplication.java:178)
        at org.apache.brooklyn.core.entity.AbstractApplication.start(AbstractApplication.java:155)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:606)
        at org.codehaus.groovy.reflection.CachedMethod.invoke(CachedMethod.java:90)
        at groovy.lang.MetaMethod.doMethodInvoke(MetaMethod.java:233)
        at groovy.lang.MetaClassImpl.invokeMethod(MetaClassImpl.java:1085)
        at groovy.lang.MetaClassImpl.invokeMethod(MetaClassImpl.java:909)
        at groovy.lang.DelegatingMetaClass.invokeMethod(DelegatingMetaClass.java:149)
        at groovy.lang.MetaObjectProtocol$invokeMethod.call(Unknown Source)
        at org.apache.brooklyn.util.groovy.GroovyJavaMethods.invokeMethodOnMetaClass(GroovyJavaMethods.java:191)
        at org.apache.brooklyn.core.mgmt.internal.AbstractManagementContext.invokeEffectorMethodLocal(AbstractManagementContext.java:304)
        at org.apache.brooklyn.core.mgmt.internal.AbstractManagementContext.invokeEffectorMethodSync(AbstractManagementContext.java:328)
        ... 11 more
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: 1 of 2 parallel child tasks failed: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=VhsCCM9k}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=VhsCCM9k} did not pass is-running check within the required 2m limit (2m 1s elapsed)
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:351)
        ... 27 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: 1 of 2 parallel child tasks failed: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=VhsCCM9k}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=VhsCCM9k} did not pass is-running check within the required 2m limit (2m 1s elapsed)
        at org.apache.brooklyn.util.exceptions.Exceptions.create(Exceptions.java:299)
        at org.apache.brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:287)
        at org.apache.brooklyn.util.core.task.ParallelTask.runJobs(ParallelTask.java:81)
        at org.apache.brooklyn.util.core.task.CompoundTask$1.call(CompoundTask.java:81)
        at org.apache.brooklyn.util.core.task.CompoundTask$1.call(CompoundTask.java:79)
        ... 5 more
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=VhsCCM9k}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=VhsCCM9k} did not pass is-running check within the required 2m limit (2m 1s elapsed)
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.ParallelTask.runJobs(ParallelTask.java:63)
        ... 7 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RubyRepNodeImpl{id=VhsCCM9k}: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=VhsCCM9k} did not pass is-running check within the required 2m limit (2m 1s elapsed)
        at org.apache.brooklyn.core.mgmt.internal.EffectorUtils.handleEffectorException(EffectorUtils.java:270)
        at org.apache.brooklyn.core.effector.EffectorTasks$EffectorBodyTaskFactory$2.handleException(EffectorTasks.java:90)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask.handleException(DynamicSequentialTask.java:452)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask$DstJob.call(DynamicSequentialTask.java:400)
        ... 5 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=VhsCCM9k} did not pass is-running check within the required 2m limit (2m 1s elapsed)
        at org.apache.brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:103)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:353)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask.drain(DynamicSequentialTask.java:475)
        at org.apache.brooklyn.util.core.task.DynamicTasks.drain(DynamicTasks.java:313)
        at org.apache.brooklyn.util.core.task.DynamicTasks.waitForLast(DynamicTasks.java:302)
        at org.apache.brooklyn.entity.software.base.lifecycle.MachineLifecycleEffectorTasks.start(MachineLifecycleEffectorTasks.java:314)
        at org.apache.brooklyn.entity.software.base.lifecycle.MachineLifecycleEffectorTasks$StartEffectorBody.call(MachineLifecycleEffectorTasks.java:214)
        at org.apache.brooklyn.entity.software.base.lifecycle.MachineLifecycleEffectorTasks$StartEffectorBody.call(MachineLifecycleEffectorTasks.java:201)
        at org.apache.brooklyn.core.effector.EffectorTasks$EffectorBodyTaskFactory$1.call(EffectorTasks.java:82)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask$DstJob.call(DynamicSequentialTask.java:342)
        ... 5 more
        Caused by: java.util.concurrent.ExecutionException: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=VhsCCM9k} did not pass is-running check within the required 2m limit (2m 1s elapsed)
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:351)
        ... 13 more
        Caused by: java.lang.IllegalStateException: Software process entity RubyRepNodeImpl{id=VhsCCM9k} did not pass is-running check within the required 2m limit (2m 1s elapsed)
        at org.apache.brooklyn.entity.software.base.SoftwareProcessImpl.waitForEntityStart(SoftwareProcessImpl.java:592)
        at org.apache.brooklyn.entity.software.base.SoftwareProcessImpl.postDriverStart(SoftwareProcessImpl.java:266)
        at org.apache.brooklyn.entity.software.base.SoftwareProcessDriverLifecycleEffectorTasks.postStartCustom(SoftwareProcessDriverLifecycleEffectorTasks.java:169)
        at org.apache.brooklyn.entity.software.base.lifecycle.MachineLifecycleEffectorTasks$PostStartTask.run(MachineLifecycleEffectorTasks.java:572)
        at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:471)
        ... 6 more
     */
    /**
     * Altered to use a single postgresql server to avoid issues with shared memory limits
     */
    @Test(groups = {"Integration","Broken"})
    public void test_localhost_postgres() throws Exception {
        String createTwoDbsScript = PostgreSqlIntegrationTest.CREATION_SCRIPT +
                PostgreSqlIntegrationTest.CREATION_SCRIPT.replaceAll("CREATE USER.*", "").replaceAll(" feedback", " feedback1");

        PostgreSqlNode db1 = tapp.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, createTwoDbsScript)
                .configure(PostgreSqlNode.POSTGRESQL_PORT, PortRanges.fromInteger(9113))
                .configure(PostgreSqlNode.MAX_CONNECTIONS, 10)
                .configure(PostgreSqlNode.SHARED_MEMORY, "512kB")); // Very low so kernel configuration not needed

        startInLocation(tapp, db1, "feedback", db1, "feedback1", new LocalhostMachineProvisioningLocation());
        testReplication(db1, "feedback", db1, "feedback1");
    }

    @Test(enabled = false, groups = "Integration") // TODO this doesn't appear to be supported by RubyRep
    public void test_localhost_postgres_mysql() throws Exception {
        PostgreSqlNode db1 = tapp.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, PostgreSqlIntegrationTest.CREATION_SCRIPT)
                .configure(PostgreSqlNode.POSTGRESQL_PORT, PortRanges.fromInteger(9115))
                .configure(PostgreSqlNode.MAX_CONNECTIONS, 10)
                .configure(PostgreSqlNode.SHARED_MEMORY, "512kB")); // Very low so kernel configuration not needed

        MySqlNode db2 = tapp.createAndManageChild(EntitySpec.create(MySqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, MySqlIntegrationTest.CREATION_SCRIPT)
                .configure(MySqlNode.MYSQL_PORT, PortRanges.fromInteger(9116)));

        startInLocation(tapp, db1, db2, new LocalhostMachineProvisioningLocation());
        testReplication(db1, db2);
    }

    public static void startInLocation(TestApplication tapp, DatastoreCommon db1, DatastoreCommon db2, Location... locations) throws Exception {
        startInLocation(tapp, db1, "feedback", db2, "feedback", locations);
    }

    /**
     * Configures rubyrep to connect to the two databases and starts the app
     */
    public static void startInLocation(TestApplication tapp, DatastoreCommon db1, String dbName1, DatastoreCommon db2, String dbName2, Location... locations) throws Exception {
        tapp.createAndManageChild(EntitySpec.create(RubyRepNode.class)
                .configure("startupTimeout", 300)
                .configure("leftDatabase", db1)
                .configure("rightDatabase", db2)
                .configure("leftUsername", "sqluser")
                .configure("rightUsername", "sqluser")
                .configure("rightPassword", "sqluserpw")
                .configure("leftPassword", "sqluserpw")
                .configure("leftDatabaseName", dbName1)
                .configure("rightDatabaseName", dbName2)
                .configure("replicationInterval", 1)
        );

        tapp.start(Arrays.asList(locations));
    }

    public static void testReplication(DatastoreCommon db1, DatastoreCommon db2) throws Exception {
        testReplication(db1, "feedback", db2, "feedback");
    }

    /**
     * Tests replication between the two databases by altering the first and checking the change is applied to the second
     */
    public static void testReplication(DatastoreCommon db1, String dbName1, DatastoreCommon db2, String dbName2) throws Exception {
        String db1Url = db1.getAttribute(DatastoreCommon.DATASTORE_URL);
        String db2Url = db2.getAttribute(DatastoreCommon.DATASTORE_URL);

        log.info("Testing replication between " + db1Url + " and " + db2Url);

        VogellaExampleAccess vea1 = new VogellaExampleAccess(db1 instanceof MySqlNode ? "com.mysql.jdbc.Driver" : "org.postgresql.Driver", db1Url, dbName1);
        VogellaExampleAccess vea2 = new VogellaExampleAccess(db2 instanceof MySqlNode ? "com.mysql.jdbc.Driver" : "org.postgresql.Driver", db2Url, dbName2);

        try {
            vea1.connect();
            List<List<String>> rs = vea1.readDataBase();
            assertEquals(rs.size(), 1);

            vea2.connect();
            rs = vea2.readDataBase();
            assertEquals(rs.size(), 1);

            log.info("Modifying left database");
            vea1.modifyDataBase();

            log.info("Reading left database");
            rs = vea1.readDataBase();
            assertEquals(rs.size(), 2);

            log.info("Reading right database");
            rs = vea2.readDataBase();

            for (int i = 0; i < 60 && rs.size() != 2; i++) {
                log.info("Sleeping for a second");
                Thread.sleep(1000);
                rs = vea2.readDataBase();
            }

            assertEquals(rs.size(), 2);
        } finally {
            vea1.close();
            vea2.close();
        }
    }
}
