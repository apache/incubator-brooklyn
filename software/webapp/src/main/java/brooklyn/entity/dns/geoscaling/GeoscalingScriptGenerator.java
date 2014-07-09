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
package brooklyn.entity.dns.geoscaling;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;

import brooklyn.location.geo.HostGeoInfo;
import brooklyn.util.ResourceUtils;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.os.Os;
import brooklyn.util.text.Strings;

public class GeoscalingScriptGenerator {
    
    private static final String PHP_SCRIPT_TEMPLATE_RESOURCE = JavaClassNames.resolveClasspathUrl(GeoscalingScriptGenerator.class, "template.php");
    private static final String HOSTS_DECLARATIONS_MARKER = "/* HOST DECLARATIONS TO BE SUBSTITUTED HERE */";
    private static final String DATESTAMP_MARKER = "DATESTAMP";

    
    public static String generateScriptString(Collection<HostGeoInfo> hosts) {
        return generateScriptString(new Date(), hosts);
    }
    
    public static String generateScriptString(Date generationTime, Collection<HostGeoInfo> hosts) {
        String template = ResourceUtils.create(GeoscalingScriptGenerator.class).getResourceAsString(PHP_SCRIPT_TEMPLATE_RESOURCE);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String datestamp = sdf.format(generationTime);
        String declarations = getHostsDeclaration(hosts);
        return template
            .replaceAll(DATESTAMP_MARKER, datestamp)
            .replace(HOSTS_DECLARATIONS_MARKER, declarations);
    }
    
    private static String getHostsDeclaration(Collection<HostGeoInfo> hosts) {
        StringBuffer sb = new StringBuffer();
        sb.append("$hosts = array(").append(Os.LINE_SEPARATOR);
        Iterator<HostGeoInfo> iServer = hosts.iterator();
        while (iServer.hasNext()) {
            HostGeoInfo server = iServer.next();
            sb.append("    array('name'      => '").append(escape(server.displayName)).append("',").append(Os.LINE_SEPARATOR);
            sb.append("          'latitude'  => ").append(server.latitude).append(",").append(Os.LINE_SEPARATOR);
            sb.append("          'longitude' => ").append(server.longitude).append(",").append(Os.LINE_SEPARATOR);
            sb.append("          'ip'        => '").append(escape(server.address)).append("')");
            if (iServer.hasNext()) sb.append(",").append(Os.LINE_SEPARATOR);
            sb.append(Os.LINE_SEPARATOR);
        }
        sb.append(");").append(Os.LINE_SEPARATOR);
        return sb.toString();
    }
    
    private static String escape(String txt) {
        txt = Strings.replaceAllNonRegex(txt, "\\", "\\\\");
        txt = Strings.replaceAllNonRegex(txt, "'", "\\'");
        txt = Strings.replaceAllNonRegex(txt, "\"", "\\\"'");
        return txt;
    }
    
}
