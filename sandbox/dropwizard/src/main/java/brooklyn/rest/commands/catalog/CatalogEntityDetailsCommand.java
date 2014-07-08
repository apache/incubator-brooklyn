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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.PrintStream;

import javax.ws.rs.core.MediaType;

import org.apache.commons.cli.CommandLine;

import brooklyn.rest.commands.BrooklynCommand;
import brooklyn.rest.domain.CatalogEntitySummary;

import com.sun.jersey.api.client.Client;
import com.yammer.dropwizard.json.Json;

public class CatalogEntityDetailsCommand extends BrooklynCommand {

  public CatalogEntityDetailsCommand() {
    super("catalog-entity", "Show details of entity type");
  }

  @Override
  public String getSyntax() {
    return "[options] <entity type>";
  }

  @Override
  protected void run(PrintStream out, PrintStream err, Json json,
                     Client client, CommandLine params) throws Exception {
    checkArgument(params.getArgList().size() >= 1, "The type of the entity is mandatory");

    String type = (String) params.getArgList().get(0);
    CatalogEntitySummary catalogEntity = client.resource(uriFor("/v1/catalog/entities/" + type))
            .type(MediaType.APPLICATION_JSON_TYPE).get(CatalogEntitySummary.class);
    out.println(catalogEntity);
  }
}
