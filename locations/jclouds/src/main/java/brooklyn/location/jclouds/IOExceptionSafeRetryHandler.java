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
package brooklyn.location.jclouds;

import java.io.IOException;
import java.util.Set;

import javax.annotation.Resource;

import org.jclouds.http.HttpCommand;
import org.jclouds.http.IOExceptionRetryHandler;
import org.jclouds.http.handlers.BackoffLimitedRetryHandler;
import org.jclouds.logging.Logger;

import com.google.common.collect.ImmutableSet;

/**
 * Even if we get an exception from a request it could've already been processed
 * by the server, so it's safe to retry only those which don't modify server state
 * (i.e. GET, HEAD).
 */
public class IOExceptionSafeRetryHandler extends BackoffLimitedRetryHandler implements IOExceptionRetryHandler {
    private static final Set<String> SAFE_METHODS = ImmutableSet.of("GET", "HEAD");

    @Resource
    protected Logger logger = Logger.NULL;

    @Override
    public boolean shouldRetryRequest(HttpCommand command, IOException error) {
        String method = command.getCurrentRequest().getMethod();
        if (SAFE_METHODS.contains(method)) {
            return super.shouldRetryRequest(command, error);
        } else {
            logger.error("Command not considered safe to retry because request method is %1$s: %2$s", method, command);
            return false;
        }
    }

}
