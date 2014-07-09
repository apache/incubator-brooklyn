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
package brooklyn.rest.entities.external

import static brooklyn.event.basic.DependentConfiguration.valueWhenAttributeReady

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.policy.autoscaling.AutoScalerPolicy
import brooklyn.entity.database.mysql.MySqlNode
import brooklyn.entity.database.mysql.MySqlNodeImpl
import brooklyn.entity.basic.UsesJava
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.event.basic.BasicConfigKey
import brooklyn.util.flags.SetFromFlag

class WebClusterWithMySQLDatabase extends AbstractApplication {

    @SetFromFlag("war_url")
    public static final BasicConfigKey<String> WAR_PATH =
        [String, "rest.webcluster.war.url", "", "classpath://hello-world-webapp.war"]

    @SetFromFlag("db_username")
    public static final BasicConfigKey<String> DB_USERNAME =
        [String, "rest.webcluster.db.username", "", "brooklyn"]

    @SetFromFlag("db_password")
    public static final BasicConfigKey<String> DB_PASSWORD =
        [String, "rest.webcluster.db.password", "", "br00k11n"]

    @SetFromFlag("db_setup_url")
    public static final BasicConfigKey<String> DB_SETUP_SQL_URL =
        [String, "rest.webcluster.db.setup.url", "", "classpath://visitors-creation-script.sql"]

    @SetFromFlag("http_port")
    public static final BasicConfigKey<String> HTTP_PORT =
        [String, "rest.webcluster.http.port", "", "8080+"]

    // TODO configs for resize policy range and resize policy metric range

    public WebClusterWithMySQLDatabase(Map props = [:]) {
        super(props)
    }

    public String makeJdbcUrl(String dbUrl) {
        //jdbc:mysql://192.168.1.2:3306/visitors?user=brooklyn&password=br00k11n
        "jdbc:" + dbUrl + "visitors" + "?" +
                "user=" + getConfig(DB_USERNAME) + "\\&" +
                "password=" + getConfig(DB_PASSWORD)
    }

    ControlledDynamicWebAppCluster web = new ControlledDynamicWebAppCluster(this, war: getConfig(WAR_PATH));
    MySqlNode mysql = new MySqlNodeImpl(this, creationScriptUrl: getConfig(DB_SETUP_SQL_URL));

    {
        web.factory.configure(
                httpPort: getConfig(HTTP_PORT),
                (UsesJava.JAVA_OPTIONS):
                        ["brooklyn.example.db.url": valueWhenAttributeReady(mysql, MySqlNode.MYSQL_URL, this.&makeJdbcUrl)]);

        web.cluster.addPolicy(AutoScalerPolicy.builder()
                .metric(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND)
                .sizeRange(1, 5)
                .metricRange(10, 100)
                .build());
    }
}
