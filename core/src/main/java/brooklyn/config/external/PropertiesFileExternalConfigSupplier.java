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
package brooklyn.config.external;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import brooklyn.util.stream.Streams;


/**
 * Instances are populated from a plain java properties file named in the passed <code>config</code> map
 * under the <code>propertiesUrl</code> key:
 *
 * <pre>
 * brooklyn.external.foo = brooklyn.management.config.external.PropertiesFileExternalConfigSupplier
 * brooklyn.external.foo.propertiesUrl = http://brooklyn.example.com/config/foo.properties
 * </pre>
 */
public class PropertiesFileExternalConfigSupplier extends AbstractExternalConfigSupplier {

    public static final String PROPERTIES_URL = "propertiesUrl";

    private final Properties properties;

    public PropertiesFileExternalConfigSupplier(String name, Map<?, ?> config) throws IOException {
        super(name);
        this.properties = loadProperties((String) config.get(PROPERTIES_URL));
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    private static Properties loadProperties(String propertiesUrl) throws IOException {
        InputStream is = null;
        try {
            is = new URL(propertiesUrl).openStream();
            Properties p = new Properties();
            p.load(is);
            return p;

        } finally {
            Streams.closeQuietly(is);
        }
    }

}
