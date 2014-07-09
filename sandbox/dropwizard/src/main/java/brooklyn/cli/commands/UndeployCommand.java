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
package brooklyn.cli.commands;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import io.airlift.command.Arguments;
import io.airlift.command.Command;
import io.airlift.command.Option;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Command(name = "undeploy", description = "Undeploys the specified application")
public class UndeployCommand extends BrooklynCommand {

    @Option(name = "--no-stop",
            description = "Don't invoke `stop` on the application")
    public boolean noStart = false;

    @Arguments(title = "APP",
            description = "where APP can be\n" +
                    "    * a fully qualified class-name of something on the classpath\n" +
                    "    * path or URL to a script file (if ends .groovy)\n" +
                    "    * path or URL to a JSON file (if ends .json)")
    public String app;

    @Override
    public void run() throws Exception {

        // Make an HTTP request to the REST server
        WebResource webResource = getClient().resource(endpoint + "/v1/applications/" + app);
        ClientResponse clientResponse = webResource.accept(MediaType.APPLICATION_JSON).delete(ClientResponse.class);

        // Make sure we get the correct HTTP response code
        if (clientResponse.getStatus() != Response.Status.ACCEPTED.getStatusCode()) {
            String err = getErrorMessage(clientResponse);
            throw new CommandExecutionException(err);
        }

        getOut().println("Application has been undeployed: " + app);
    }

}


