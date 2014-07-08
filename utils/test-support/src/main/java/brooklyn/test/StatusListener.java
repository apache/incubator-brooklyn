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

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IClass;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * adapted from the following class:
 * 
 * @see org.jclouds.test.testng.UnitTestStatusListener
 * 
 * normally not used, preferring instead LoggingVerboseReporter which prints out config info
 */
public class StatusListener implements ITestListener {
	
	public static final Logger log = LoggerFactory.getLogger(StatusListener.class);
	
    /**
     * Holds test classes actually running in all threads.
     */
    private ThreadLocal<IClass> threadTestClass = new ThreadLocal<IClass>();
    private ThreadLocal<Long> threadTestStart = new ThreadLocal<Long>();

    private AtomicInteger failed = new AtomicInteger(0);
    private AtomicInteger succeded = new AtomicInteger(0);
    private AtomicInteger skipped = new AtomicInteger(0);

    //TODO instead of system.out.println we should log -- *and* perhaps write to sysout if logger doesn't?
    protected static void log(String msg) {
    	log.info(msg);
    }
    
    public void onTestStart(ITestResult res) {
        log("Starting test " + getTestDesc(res));
        threadTestClass.set(res.getTestClass());
        threadTestStart.set(System.currentTimeMillis());
    }

    synchronized public void onTestSuccess(ITestResult arg0) {
        log(getThreadId() + " Test " + getTestDesc(arg0) + " succeeded: " + (System.currentTimeMillis() - threadTestStart.get()) + "ms");
        succeded.incrementAndGet();
        printStatus();
    }

    synchronized public void onTestFailure(ITestResult arg0) {
        log(getThreadId() + " Test " + getTestDesc(arg0) + " failed: "+arg0.getThrowable());
        failed.incrementAndGet();
        printStatus();
    }

    synchronized public void onTestSkipped(ITestResult arg0) {
        System.out.println(getThreadId() + " Test " + getTestDesc(arg0) + " skipped.");
        skipped.incrementAndGet();
        printStatus();
    }

    public void onTestFailedButWithinSuccessPercentage(ITestResult arg0) {
    }

    public void onStart(ITestContext arg0) {
    }

    public void onFinish(ITestContext arg0) {
    }

    private String getThreadId() {
        return "[" + Thread.currentThread().getName() + "]";
    }

    private String getTestDesc(ITestResult res) {
        return res.getMethod().getMethodName() + "(" + res.getTestClass().getName() + ")";
    }

    private void printStatus() {
        log("Test suite progress: tests succeeded: " + succeded.get() + ", failed: " + failed.get() + ", skipped: " + skipped.get() + ".");
    }
}
