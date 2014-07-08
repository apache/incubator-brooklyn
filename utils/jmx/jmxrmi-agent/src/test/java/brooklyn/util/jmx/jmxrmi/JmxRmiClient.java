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
package brooklyn.util.jmx.jmxrmi;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

@SuppressWarnings({"rawtypes","unchecked"})
public class JmxRmiClient {

    public void connect(String urlString, Map env) throws MalformedURLException, IOException {
        JMXServiceURL url = new JMXServiceURL(urlString);
        System.out.println("JmxmpClient connecting to "+url);
        JMXConnector jmxc = JMXConnectorFactory.connect(url, env); 
        
        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection(); 
        String domains[] = mbsc.getDomains(); 
        for (int i = 0; i < domains.length; i++) { 
            System.out.println("Domain[" + i + "] = " + domains[i]); 
        } 

        jmxc.close();
    } 

}
