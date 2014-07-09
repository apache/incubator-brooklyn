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
import com.google.common.collect.ImmutableMap;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.yammer.dropwizard.json.Json;
import org.apache.commons.cli.CommandLine;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.PrintStream;
import java.net.URI;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class InvokeEffectorCommand extends BrooklynCommand {

  public InvokeEffectorCommand() {
    super("invoke-effector", "Invoke entity effector (no arguments)");
  }

  @Override
  public String getSyntax() {
    return "[options] <effector uri>";
  }

  @Override
  protected void run(PrintStream out, PrintStream err, Json json,
                     Client client, CommandLine params) throws Exception {
    checkArgument(params.getArgList().size() >= 1, "Effector URI is mandatory");

    URI effectorUri = uriFor((String) params.getArgList().get(0));
    ClientResponse response = client.resource(effectorUri)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(ImmutableMap.<String, String>of())
        .post(ClientResponse.class);

    checkState(response.getStatus() == Response.Status.ACCEPTED.getStatusCode(),
        "Got unexpected response: " + response.toString());
  }
}
