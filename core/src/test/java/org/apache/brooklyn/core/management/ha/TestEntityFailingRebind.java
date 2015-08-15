/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.core.management.ha;

import org.apache.brooklyn.test.entity.TestApplicationImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestEntityFailingRebind extends TestApplicationImpl {
    
    private static final Logger LOG = LoggerFactory.getLogger(TestEntityFailingRebind.class);

    public static class RebindException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public RebindException(String message) {
            super(message);
        }
    }
    
    private static boolean throwOnRebind = true;
    
    public static void setThrowOnRebind(boolean state) {
        throwOnRebind = state;
    }
    
    public static boolean getThrowOnRebind() {
        return throwOnRebind;
    }

    @Override
    public void rebind() {
        if (throwOnRebind) {
            LOG.warn("Throwing intentional exception to simulate failure of rebinding " + this);
            throw new RebindException("Intentional exception thrown when rebinding " + this);
        }
    }

}
