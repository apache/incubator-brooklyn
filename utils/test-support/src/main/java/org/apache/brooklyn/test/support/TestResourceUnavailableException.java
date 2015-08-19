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
package org.apache.brooklyn.test.support;

import com.google.common.base.Throwables;
import org.testng.SkipException;

import java.io.IOException;
import java.io.InputStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Indicates that a test cannot be run as a necessary resource is not available.
 *
 * <p>This exception should be used by tests that need a particular test resource (i.e., a file that is conventionally
 * in the <code>src/test/resources</code> folder, and that available as a classpath resource at runtime) is not
 * available. It will cause TestNG to mark the test as "skipped" with a suitable message.</p>
 *
 * <p>Some tests require binary files (such as OSGi bundles) which under Apache conventions are not able to be
 * distributed in our source code release. This exception allows such tests to become "optional" so that they do not
 * cause test failures when running the tests on a source distribution.</p>
 *
 * <p>Note that the single-string constructors expect the string to be the simple name of the classpath resource that
 * is not available. The exception message is then derived from this. The two-string constructors take both the
 * resource name and an explicit exception message.</p>
 */
@SuppressWarnings("UnusedDeclaration")
public class TestResourceUnavailableException extends SkipException {

    private final String resourceName;

    /**
     * Asserts that a resource is available on the classpath; otherwise, throws {@link TestResourceUnavailableException}
     *
     * Note that this will use the same classloader that was used to load this class.
     *
     * @param resourceName the classpath resource name, e.g.
     *                     <code>/brooklyn/osgi/brooklyn-test-osgi-entities.jar</code>
     */
    public static void throwIfResourceUnavailable(Class<?> relativeToClass, String resourceName) {
        checkNotNull(relativeToClass, relativeToClass);
        checkNotNull(resourceName, "resourceName");
        checkArgument(!resourceName.isEmpty(), "resourceName must not be empty");
        InputStream resource = relativeToClass.getResourceAsStream(resourceName);
        if (resource == null)
            throw new TestResourceUnavailableException(resourceName);

        // just make sure we clean up the resource
        try {
            resource.close();
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    /**
     * Instantiate an exception, giving the name of the unavailable resource.
     *
     * @param resourceName the name of the resource on the classpath, e.g.
     *                     <code>/brooklyn/osgi/brooklyn-test-osgi-entities.jar</code>
     */
    public TestResourceUnavailableException(String resourceName) {
        super(messageFromResourceName(resourceName));
        this.resourceName = resourceName;
    }

    /**
     * Instantiate an exception, giving the name of the unavailable resource.
     *
     * @param resourceName the name of the resource on the classpath, e.g.
     *                     <code>/brooklyn/osgi/brooklyn-test-osgi-entities.jar</code>
     * @param cause the underlying exception that caused this one
     */
    public TestResourceUnavailableException(String resourceName, Throwable cause) {
        super(messageFromResourceName(resourceName), cause);
        this.resourceName = resourceName;
    }

    /**
     * Instantiate an exception, giving the name of the unavailable resource.
     *
     * @param resourceName the name of the resource on the classpath, e.g.
     *                     <code>/brooklyn/osgi/brooklyn-test-osgi-entities.jar</code>
     * @param skipMessage the message associated with the exception
     */
    public TestResourceUnavailableException(String resourceName, String skipMessage) {
        super(skipMessage);
        this.resourceName = resourceName;
    }

    /**
     * Instantiate an exception, giving the name of the unavailable resource.
     *
     * @param resourceName the name of the resource on the classpath, e.g.
     *                     <code>/brooklyn/osgi/brooklyn-test-osgi-entities.jar</code>
     * @param skipMessage the message associated with the exception
     * @param cause the underlying exception that caused this one
     */
    public TestResourceUnavailableException(String resourceName, String skipMessage, Throwable cause) {
        super(skipMessage, cause);
        this.resourceName = resourceName;
    }

    private static String messageFromResourceName(String resourceName) {
        return String.format("Test resource '%s' not found; test skipped.", resourceName);
    }

    /**
     * Get the name of the classpath resource that could not be loaded.
     *
     * @return the name of the classpath resource whose absence caused this exception.
     */
    public String getResourceName() {
        return resourceName;
    }

    @Override
    public boolean isSkip() {
        return true;
    }

}
