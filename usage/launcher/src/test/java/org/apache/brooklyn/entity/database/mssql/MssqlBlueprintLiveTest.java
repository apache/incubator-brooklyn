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
package org.apache.brooklyn.entity.database.mssql;

import java.io.StringReader;
import java.util.Map;

import org.testng.annotations.Test;
import org.apache.brooklyn.launcher.blueprints.AbstractBlueprintTest;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.text.TemplateProcessor;

import com.google.common.collect.ImmutableMap;

/**
 * Assumes that brooklyn.properties contains something like the following (but with real values!):
 * 
 * {@code
 * test.mssql.download.url = http://myserver.com/sql2012.iso
 * test.mssql.download.user = myname
 * test.mssql.download.password = mypassword
 * test.mssql.sa.password = mypassword
 * test.mssql.instance.name = MYNAME
 * }
 */
public class MssqlBlueprintLiveTest extends AbstractBlueprintTest {

    // TODO Needs further testing
    
    @Test(groups={"Live"})
    public void testMssql() throws Exception {
        Map<String, String> substitutions = ImmutableMap.of(
                "mssql.download.url", mgmt.getConfig().getFirst("test.mssql.download.url"),
                "mssql.download.user", mgmt.getConfig().getFirst("test.mssql.download.user"),
                "mssql.download.password", mgmt.getConfig().getFirst("test.mssql.download.password"),
                "mssql.sa.password", mgmt.getConfig().getFirst("test.mssql.sa.password"),
                "mssql.instance.name", mgmt.getConfig().getFirst("test.mssql.instance.name"));

        String rawYaml = new ResourceUtils(this).getResourceAsString("mssql-test.yaml");
        String yaml = TemplateProcessor.processTemplateContents(rawYaml, substitutions);
        runTest(new StringReader(yaml));
    }
}
