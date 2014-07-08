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
package brooklyn.rest.commands.catalog;

import brooklyn.rest.commands.BrooklynCommand;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.yammer.dropwizard.json.Json;
import org.apache.commons.cli.CommandLine;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.Charset;

import static com.google.common.base.Preconditions.checkArgument;

public class LoadClassCommand extends BrooklynCommand {

  public LoadClassCommand() {
    super("load-class", "Load code from external groovy files.");
  }

  @Override
  public String getSyntax() {
    return "[options] <groovy file>";
  }

  @Override
  protected void run(PrintStream out, PrintStream err, Json json,
                     Client client, CommandLine params) throws Exception {
    checkArgument(params.getArgList().size() >= 1, "Path to Groovy file is mandatory.");

    String scriptFileName = (String) params.getArgList().get(0);
    String groovyScript = Joiner.on("\n").join(Files.readLines(new File(scriptFileName),
        Charset.forName("utf-8")));

    ClientResponse response = client.resource(uriFor("/v1/catalog"))
        .type(MediaType.APPLICATION_JSON_TYPE).post(ClientResponse.class, groovyScript);

    out.println("Ok, create: " + response.getLocation());
  }
}
