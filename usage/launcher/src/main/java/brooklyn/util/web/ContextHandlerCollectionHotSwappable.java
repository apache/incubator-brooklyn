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
package brooklyn.util.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.webapp.WebAppContext;

public class ContextHandlerCollectionHotSwappable extends ContextHandlerCollection {

    public synchronized void updateHandler(WebAppContext context) throws Exception {
        Handler[] hl0 = getHandlers();
        List<Handler> hl = hl0!=null ? new ArrayList<Handler>(Arrays.asList(hl0)) : new ArrayList<Handler>();
        // remove any previous version
        removeContextFromList(hl, context.getContextPath());
        // have to add before the root war (remove root war then add back)
        Handler oldRoot = removeContextFromList(hl, "/");
        // now add and add back any root
        hl.add(context);
        if (oldRoot!=null) hl.add(oldRoot);
        setHandlers(hl.toArray(new Handler[0]));
        
        // and if we are already running, start the new context
        if (isRunning()) {
            context.start();
        }
    }

    public static Handler removeContextFromList(List<Handler> hl, String contextPath) {
        Iterator<Handler> hi = hl.iterator();
        while (hi.hasNext()) {
            Handler h = hi.next();
            if ((h instanceof WebAppContext) && ((WebAppContext)h).getContextPath().equals(contextPath)) {
                hi.remove();
                return h;
            }
        }
        return null;
    }

}
