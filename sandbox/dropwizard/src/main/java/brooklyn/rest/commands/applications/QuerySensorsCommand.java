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
package brooklyn.rest.commands.applications;

import brooklyn.rest.commands.BrooklynCommand;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.EntitySummary;
import brooklyn.rest.domain.SensorSummary;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.GenericType;
import com.yammer.dropwizard.json.Json;
import org.apache.commons.cli.CommandLine;

import javax.ws.rs.core.MediaType;
import java.io.PrintStream;
import java.net.URI;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public class QuerySensorsCommand extends BrooklynCommand {

  public QuerySensorsCommand() {
    super("query-sensors", "Query all application sensors for all entities");
  }

  @Override
  public String getSyntax() {
    return "[options] <application name>";
  }

  @Override
  protected void run(PrintStream out, PrintStream err, Json json,
                     Client client, CommandLine params) throws Exception {
    checkArgument(params.getArgList().size() >= 1, "Application name is mandatory");

    String name = (String) params.getArgList().get(0);
    ApplicationSummary application = client.resource(uriFor("/v1/applications/" + name))
        .type(MediaType.APPLICATION_JSON_TYPE).get(ApplicationSummary.class);

    queryAllEntities(out, client, application.getLinks().get("entities"));
  }

  private void queryAllEntities(PrintStream out, Client client, URI resource) {
    Set<EntitySummary> entities = client.resource(expandIfRelative(resource))
        .type(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<Set<EntitySummary>>() {
        });

    for (EntitySummary summary : entities) {
      out.println(summary.getLinks().get("self") + " #" + summary.getType());
      queryAllSensors(out, client, summary.getLinks().get("sensors"));
      queryAllEntities(out, client, summary.getLinks().get("children"));
    }
  }

  private void queryAllSensors(PrintStream out, Client client, URI sensorsUri) {
    Set<SensorSummary> sensors = client.resource(expandIfRelative(sensorsUri))
        .type(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<Set<SensorSummary>>() {
        });
    for (SensorSummary summary : sensors) {
      String value = client.resource(expandIfRelative(summary.getLinks().get("self")))
          .type(MediaType.APPLICATION_JSON_TYPE).get(String.class);
      out.println("\t" + summary.getName() + " = " + value);
    }
    out.println();
  }
}
