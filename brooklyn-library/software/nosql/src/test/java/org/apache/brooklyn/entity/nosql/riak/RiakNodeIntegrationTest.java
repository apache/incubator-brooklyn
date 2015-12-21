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
package org.apache.brooklyn.entity.nosql.riak;

import static org.testng.Assert.assertFalse;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.test.EntityTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

public class RiakNodeIntegrationTest {

    private TestApplication app;
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        localhostProvisioningLocation = new LocalhostMachineProvisioningLocation();
        app = TestApplication.Factory.newManagedInstanceForTests();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }


    /*
        Exception org.apache.brooklyn.util.exceptions.PropagatedRuntimeException
        
        Message: (none)
        Stacktrace:
        
        at org.apache.brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:103)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:353)
        at org.apache.brooklyn.util.core.task.DynamicTasks$TaskQueueingResult.andWaitForSuccess(DynamicTasks.java:159)
        at org.apache.brooklyn.core.objs.proxy.EntityProxyImpl.invoke(EntityProxyImpl.java:211)
        at com.sun.proxy.$Proxy267.start(Unknown Source)
        at org.apache.brooklyn.entity.nosql.riak.RiakNodeIntegrationTest.testCanStartAndStop(RiakNodeIntegrationTest.java:57)
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
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at Application[bHAUtUrk]: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RiakNodeImpl{id=EA5VTSqO}: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:351)
        ... 32 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at Application[bHAUtUrk]: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RiakNodeImpl{id=EA5VTSqO}: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
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
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RiakNodeImpl{id=EA5VTSqO}: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
        at org.apache.brooklyn.core.mgmt.internal.AbstractManagementContext.invokeEffectorMethodSync(AbstractManagementContext.java:332)
        at org.apache.brooklyn.core.mgmt.internal.EffectorUtils.invokeMethodEffector(EffectorUtils.java:250)
        ... 10 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RiakNodeImpl{id=EA5VTSqO}: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
        at org.apache.brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:103)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:353)
        at org.apache.brooklyn.core.entity.trait.StartableMethods.start(StartableMethods.java:53)
        at org.apache.brooklyn.core.entity.AbstractApplication.doStart(AbstractApplication.java:178)
        at org.apache.brooklyn.core.entity.AbstractApplication.start(AbstractApplication.java:155)
        at sun.reflect.GeneratedMethodAccessor41.invoke(Unknown Source)
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
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RiakNodeImpl{id=EA5VTSqO}: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:351)
        ... 26 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RiakNodeImpl{id=EA5VTSqO}: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
        at org.apache.brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:103)
        at org.apache.brooklyn.util.core.task.ParallelTask.runJobs(ParallelTask.java:80)
        at org.apache.brooklyn.util.core.task.CompoundTask$1.call(CompoundTask.java:81)
        at org.apache.brooklyn.util.core.task.CompoundTask$1.call(CompoundTask.java:79)
        ... 5 more
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RiakNodeImpl{id=EA5VTSqO}: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.ParallelTask.runJobs(ParallelTask.java:63)
        ... 7 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: Error invoking start at RiakNodeImpl{id=EA5VTSqO}: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
        at org.apache.brooklyn.core.mgmt.internal.EffectorUtils.handleEffectorException(EffectorUtils.java:270)
        at org.apache.brooklyn.core.effector.EffectorTasks$EffectorBodyTaskFactory$2.handleException(EffectorTasks.java:90)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask.handleException(DynamicSequentialTask.java:452)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask$DstJob.call(DynamicSequentialTask.java:400)
        ... 5 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
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
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:351)
        ... 13 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
        at org.apache.brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:103)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:353)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask.drain(DynamicSequentialTask.java:475)
        at org.apache.brooklyn.util.core.task.DynamicTasks.drain(DynamicTasks.java:313)
        at org.apache.brooklyn.util.core.task.DynamicTasks.waitForLast(DynamicTasks.java:302)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask$DstJob.call(DynamicSequentialTask.java:383)
        ... 5 more
        Caused by: java.util.concurrent.ExecutionException: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:351)
        ... 9 more
        Caused by: org.apache.brooklyn.util.exceptions.PropagatedRuntimeException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
        at org.apache.brooklyn.util.exceptions.Exceptions.propagate(Exceptions.java:103)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:353)
        at org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper.execute(ScriptHelper.java:337)
        at org.apache.brooklyn.entity.nosql.riak.RiakNodeSshDriver.launch(RiakNodeSshDriver.java:335)
        at org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessDriver$11.run(AbstractSoftwareProcessDriver.java:163)
        at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:471)
        at org.apache.brooklyn.util.core.task.DynamicSequentialTask$DstJob.call(DynamicSequentialTask.java:342)
        ... 5 more
        Caused by: java.util.concurrent.ExecutionException: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
        at java.util.concurrent.FutureTask.report(FutureTask.java:122)
        at java.util.concurrent.FutureTask.get(FutureTask.java:188)
        at com.google.common.util.concurrent.ForwardingFuture.get(ForwardingFuture.java:63)
        at org.apache.brooklyn.util.core.task.BasicTask.get(BasicTask.java:342)
        at org.apache.brooklyn.util.core.task.BasicTask.getUnchecked(BasicTask.java:351)
        ... 10 more
        Caused by: java.lang.IllegalStateException: Execution failed, invalid result 1 for launching RiakNodeImpl{id=EA5VTSqO}
        at org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper.logWithDetailsAndThrow(ScriptHelper.java:388)
        at org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper.executeInternal(ScriptHelper.java:377)
        at org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper$8.call(ScriptHelper.java:287)
        at org.apache.brooklyn.entity.software.base.lifecycle.ScriptHelper$8.call(ScriptHelper.java:285)
        ... 6 more
     */
    @Test(groups = {"Integration","Broken"})
    public void testCanStartAndStop() throws Exception {
        RiakNode entity = app.createAndManageChild(EntitySpec.create(RiakNode.class)
                .configure(RiakNode.SUGGESTED_VERSION, "2.1.1"));
        app.start(ImmutableList.of(localhostProvisioningLocation));

        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);
        entity.stop();
        assertFalse(entity.getAttribute(Startable.SERVICE_UP));
    }

}
