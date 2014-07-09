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
package brooklyn.rest.domain;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

// FIXME change name, due to confusion with brooklyn.location.LocationSpec <- no need, as we can kill the class instead soon!
/** @deprecated since 0.7.0 location spec objects will not be used from the client, instead pass yaml location spec strings */
public class LocationSpec implements HasName {

  @JsonSerialize(include=Inclusion.NON_NULL)
  private final String name;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final String spec;
  
  @JsonSerialize(include=Inclusion.NON_EMPTY)
  private final Map<String, ?> config;

  public static LocationSpec localhost() {
    return new LocationSpec("localhost", "localhost", null);
  }

  public LocationSpec(
      @JsonProperty("name") String name,
      @JsonProperty("spec") String spec,
      @JsonProperty("config") @Nullable Map<String, ?> config
  ) {
    this.name = name;
    this.spec = spec;
    this.config = (config == null) ? Collections.<String, String>emptyMap() : ImmutableMap.copyOf(config);
  }

  @Override
  public String getName() {
    return name;
}
  
  public String getSpec() {
    return spec;
  }

  public Map<String, ?> getConfig() {
    return config;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LocationSpec that = (LocationSpec) o;
    return Objects.equal(name, that.name) && Objects.equal(spec, that.spec) && Objects.equal(config, that.config);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(spec, name, config);
  }

  @Override
  public String toString() {
    return "LocationSpec{" +
        "name='" + name + '\'' +
        "spec='" + spec + '\'' +
        ", config=" + config +
        '}';
  }
}
