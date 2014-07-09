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

import java.util.Set;
import org.codehaus.jackson.annotate.JsonProperty;

public class User {

  private final String name;

  private final String clearTextPassword;

  private final Set<String> roles;

  public User(
      @JsonProperty("name") String name,
      @JsonProperty("password") String clearTextPassword,
      @JsonProperty("roles") Set<String> roles
  ) {
    this.name = name;
    this.clearTextPassword = clearTextPassword;
    this.roles = roles;
  }

  public String getName() {
    return name;
  }

  public String getClearTextPassword() {
    return clearTextPassword;
  }

  public Set<String> getRoles() {
    return roles;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    User user = (User) o;

    if (clearTextPassword != null ? !clearTextPassword.equals(user.clearTextPassword) : user.clearTextPassword != null)
      return false;
    if (name != null ? !name.equals(user.name) : user.name != null) return false;
    if (roles != null ? !roles.equals(user.roles) : user.roles != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (clearTextPassword != null ? clearTextPassword.hashCode() : 0);
    result = 31 * result + (roles != null ? roles.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "User{" +
        "name='" + name + '\'' +
        ", roles=" + roles +
        '}';
  }
}
