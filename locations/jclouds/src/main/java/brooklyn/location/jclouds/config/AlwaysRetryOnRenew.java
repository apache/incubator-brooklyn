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
package brooklyn.location.jclouds.config;

import static org.jclouds.http.HttpUtils.closeClientButKeepContentStream;
import static org.jclouds.http.HttpUtils.releasePayload;

import java.lang.reflect.Method;

import javax.annotation.Resource;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jclouds.domain.Credentials;
import org.jclouds.http.HttpCommand;
import org.jclouds.http.HttpResponse;
import org.jclouds.http.HttpRetryHandler;
import org.jclouds.logging.Logger;
import org.jclouds.openstack.domain.AuthenticationResponse;
import org.jclouds.openstack.handlers.RetryOnRenew;
import org.jclouds.openstack.reference.AuthHeaders;

import com.google.common.annotations.Beta;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Multimap;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;

/** Fix for RetryOnRenew so that it always retries on 401 when using a token.
 *  The "lease renew" text is not necessarily returned from swift servers.
 *  <p>
 *  See https://issues.apache.org/jira/browse/BROOKLYN-6 .
 *  When https://issues.apache.org/jira/browse/JCLOUDS-615 is fixed in the jclouds we use,
 *  we can remove this. 
 *  <p>
 *  (Marked Beta as this will likely be removed.)
 *  
 *  @since 1.7.0 */
@Beta
@Singleton
public class AlwaysRetryOnRenew implements HttpRetryHandler {
   @Resource
   protected Logger logger = Logger.NULL;

   private final LoadingCache<Credentials, AuthenticationResponse> authenticationResponseCache;

   @Inject
   protected AlwaysRetryOnRenew(LoadingCache<Credentials, AuthenticationResponse> authenticationResponseCache) {
      this.authenticationResponseCache = authenticationResponseCache;
   }

   @Override
   public boolean shouldRetryRequest(HttpCommand command, HttpResponse response) {
      boolean retry = false; // default
      try {
         switch (response.getStatusCode()) {
            case 401:
               // Do not retry on 401 from authentication request
               Multimap<String, String> headers = command.getCurrentRequest().getHeaders();
               if (headers != null && headers.containsKey(AuthHeaders.AUTH_USER)
                        && headers.containsKey(AuthHeaders.AUTH_KEY) && !headers.containsKey(AuthHeaders.AUTH_TOKEN)) {
                  retry = false;
               } else {
                  closeClientButKeepContentStream(response);
                  authenticationResponseCache.invalidateAll();
                  retry = true;
                  
                  // always retry. not all swift servers say 'lease renew', e.g. softlayer
                  
//                  byte[] content = closeClientButKeepContentStream(response);
//                  if (content != null && new String(content).contains("lease renew")) {
//                     logger.debug("invalidating authentication token");
//                     authenticationResponseCache.invalidateAll();
//                     retry = true;
//                  } else {
//                     retry = false;
//                  }
               }
               break;
         }
         return retry;

      } finally {
         releasePayload(response);
      }
   }

   /** 
    * Intercepts calls to the *other* RetryOnRenew instance, and uses the one above.
    * It's messy, but the only way I could find in the maze of guice. */
   public static class InterceptRetryOnRenewModule extends AbstractModule {
       AlwaysRetryOnRenew intereceptingRetryOnRenew;

       public void inject(Injector injector) {
           intereceptingRetryOnRenew = injector.getInstance(AlwaysRetryOnRenew.class);
       }
       
       @Override
       protected void configure() {
           Matcher<? super Class<?>> classMatcher = Matchers.subclassesOf(RetryOnRenew.class);
           Matcher<? super Method> methodMatcher = new AbstractMatcher<Method>() {
               @Override
               public boolean matches(Method t) {
                   return "shouldRetryRequest".matches(t.getName());
               }
           };
           MethodInterceptor interceptors = new MethodInterceptor() {
               @Override
               public Object invoke(MethodInvocation invocation) throws Throwable {
                   if (intereceptingRetryOnRenew==null)
                       throw new IllegalStateException("inject() must be called to set up before use");
                   return intereceptingRetryOnRenew.shouldRetryRequest((HttpCommand)invocation.getArguments()[0], (HttpResponse)invocation.getArguments()[1]);
               }
           };
           bindInterceptor(classMatcher, methodMatcher, interceptors);
       }
   }
}
