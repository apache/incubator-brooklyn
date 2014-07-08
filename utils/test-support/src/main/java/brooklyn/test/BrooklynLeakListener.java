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
package brooklyn.test;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.TestListenerAdapter;

public class BrooklynLeakListener extends TestListenerAdapter {

    private static final Logger TEST_RESOURCE_LOG = LoggerFactory.getLogger("test.resource.usage");
    
    @Override
    public void onStart(ITestContext testContext) {
        super.onStart(testContext);
        tryTerminateAll("BrooklynLeakListener.onStart", testContext);
    }
    
    @Override
    public void onFinish(ITestContext testContext) {
        super.onFinish(testContext);
        tryTerminateAll("BrooklynLeakListener.onFinish", testContext);
    }
    
    /**
     * Tries to reflectively invoke {@code LocalManagementContext.logAll(TEST_RESOURCE_LOG); LocalManagementContext.terminateAll()}.
     * 
     * It does this reflectively because the listener is executed for all projects, included those that don't
     * depend on brooklyn-core, so LocalManagementContext may not be on the classpath.
     */
    private void tryTerminateAll(String context, ITestContext testContext) {
        String clazzName = "brooklyn.management.internal.LocalManagementContext";
        String message;
        int level;
        try {
            Class<?> clazz = BrooklynLeakListener.class.getClassLoader().loadClass(clazzName);
            clazz.getMethod("logAll", new Class[] {Logger.class}).invoke(null, TEST_RESOURCE_LOG);
            Integer count = (Integer)clazz.getMethod("terminateAll").invoke(null);
            if (count>0) {
                level = 0;
                message = ""+count+" ManagementContexts terminated";
            } else if (count<0) {
                level = 1;
                message = ""+(-count)+" ManagementContexts left dangling";
            } else {
                level = -1;
                message = ""+count+" ManagementContexts terminated";
            }
        } catch (ClassNotFoundException e) {
            TEST_RESOURCE_LOG.debug("Class {} not found in testng listener, so not attempting to terminate all extant ManagementContexts; continuing", clazzName);
            level = 0;
            message = "no "+clazzName+" available, so skipped";
        } catch (Throwable e) {
            TEST_RESOURCE_LOG.error("ERROR in testng listener, attempting to terminate all extant ManagementContexts", e);
            level = 1;
            message = "ERROR: "+e;
        }
        
        String logMessage = context+" attempting to terminate all extant ManagementContexts: "
            + "name=" + testContext.getName()
            + "; includedGroups="+Arrays.toString(testContext.getIncludedGroups())
            + "; excludedGroups="+Arrays.toString(testContext.getExcludedGroups())
            + "; suiteName="+testContext.getSuite().getName()
            + "; outDir="+testContext.getOutputDirectory()
            + ": "+message;
        if (level<0) TEST_RESOURCE_LOG.debug(logMessage);
        else if (level>0) TEST_RESOURCE_LOG.warn(logMessage);
        else TEST_RESOURCE_LOG.info(logMessage);
    }
}
