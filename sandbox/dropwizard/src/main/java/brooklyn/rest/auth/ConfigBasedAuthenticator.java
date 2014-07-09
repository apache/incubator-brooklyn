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
package brooklyn.rest.auth;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.yammer.dropwizard.auth.AuthenticationException;
import com.yammer.dropwizard.auth.Authenticator;
import com.yammer.dropwizard.auth.basic.BasicCredentials;
import java.util.Map;
import java.util.Set;

/**
 * An authenticator that's using a list of accounts defined
 * in the application configuration file
 */
public class ConfigBasedAuthenticator implements Authenticator<BasicCredentials, User> {

  private final Map<String, User> userByNameIndex;

  public ConfigBasedAuthenticator(Set<User> users) {
    ImmutableMap.Builder<String, User> builder = ImmutableMap.builder();
    for (User user : users) {
      builder.put(user.getName(), user);
    }
    userByNameIndex = builder.build();
  }

  @Override
  public Optional<User> authenticate(BasicCredentials credentials)
      throws AuthenticationException {
    User user = userByNameIndex.get(credentials.getUsername());
    if (user == null || !user.getClearTextPassword().equals(credentials.getPassword())) {
      return Optional.absent();
    }
    return Optional.of(user);
  }
}
