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

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.GenericType;
import com.yammer.dropwizard.json.Json;
import org.apache.commons.cli.CommandLine;

import javax.ws.rs.core.MediaType;
import java.io.PrintStream;
import java.util.List;

public class ListApplicationsCommand extends BrooklynCommand {

  public ListApplicationsCommand() {
    super("list-applications", "List all registered applications");
  }

  @Override
  protected void run(PrintStream out, PrintStream err, Json json,
                     Client client, CommandLine params) throws Exception {
    List<ApplicationSummary> applications = client.resource(uriFor("/v1/applications"))
        .type(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<List<ApplicationSummary>>() {
        });

    String tableFormat = "%20s %10s\n";
    out.printf(tableFormat, "Application", "Status");

    for (ApplicationSummary application : applications) {
      out.printf(tableFormat, application.getSpec().getName(), application.getStatus());
    }
  }
}
