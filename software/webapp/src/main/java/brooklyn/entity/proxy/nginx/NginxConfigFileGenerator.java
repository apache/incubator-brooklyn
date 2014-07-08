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
package brooklyn.entity.proxy.nginx;

import static java.lang.String.format;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.proxy.ProxySslConfig;
import brooklyn.util.text.Strings;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

/**
 * Generates a configuration file for {@link NginxController}.
 */
public class NginxConfigFileGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(NginxConfigFileGenerator.class);

    private NginxDriver driver;
    private NginxController nginx;

    public static NginxConfigFileGenerator generator(NginxDriver driver) {
        return new NginxConfigFileGenerator(driver);
    }

    private NginxConfigFileGenerator(NginxDriver driver) {
        this.driver = driver;
        this.nginx = (NginxController) driver.getEntity();
    }

    public String configFile() {
        StringBuilder config = new StringBuilder();
        config.append("\n");
        config.append(format("pid %s;\n", driver.getPidFile()));
        config.append("events {\n");
        config.append("  worker_connections 8196;\n");
        config.append("}\n");
        config.append("http {\n");

        ProxySslConfig globalSslConfig = nginx.getSslConfig();

        if (nginx.isSsl()) {
            verifyConfig(globalSslConfig);
            appendSslConfig("global", config, "    ", globalSslConfig, true, true);
        }

        // If no servers, then defaults to returning 404
        // TODO Give nicer page back
        if (nginx.getDomain()!=null || nginx.getServerPoolAddresses() == null || nginx.getServerPoolAddresses().isEmpty()) {
            config.append("  server {\n");
            config.append(getCodeForServerConfig());
            config.append("    listen "+nginx.getPort()+";\n");
            config.append(getCodeFor404());
            config.append("  }\n");
        }

        // For basic round-robin across the server-pool
        if (nginx.getServerPoolAddresses() != null && nginx.getServerPoolAddresses().size() > 0) {
            config.append(format("  upstream "+nginx.getId()+" {\n"));
            if (nginx.isSticky()){
                config.append("    sticky;\n");
            }
            for (String address : nginx.getServerPoolAddresses()) {
                config.append("    server "+address+";\n");
            }
            config.append("  }\n");
            config.append("  server {\n");
            config.append(getCodeForServerConfig());
            config.append("    listen "+nginx.getPort()+";\n");
            if (nginx.getDomain()!=null)
                config.append("    server_name "+nginx.getDomain()+";\n");
            config.append("    location / {\n");
            config.append("      proxy_pass "+(globalSslConfig != null && globalSslConfig.getTargetIsSsl() ? "https" : "http")+"://"+nginx.getId()+";\n");
            config.append("    }\n");
            config.append("  }\n");
        }

        // For mapping by URL
        Iterable<UrlMapping> mappings = nginx.getUrlMappings();
        Multimap<String, UrlMapping> mappingsByDomain = LinkedHashMultimap.create();
        for (UrlMapping mapping : mappings) {
            Collection<String> addrs = mapping.getAttribute(UrlMapping.TARGET_ADDRESSES);
            if (addrs != null && addrs.size() > 0) {
                mappingsByDomain.put(mapping.getDomain(), mapping);
            }
        }

        for (UrlMapping um : mappings) {
            Collection<String> addrs = um.getAttribute(UrlMapping.TARGET_ADDRESSES);
            if (addrs != null && addrs.size() > 0) {
                config.append(format("  upstream "+um.getUniqueLabel()+" {\n"));
                if (nginx.isSticky()){
                    config.append("    sticky;\n");
                }
                for (String address: addrs) {
                    config.append("    server "+address+";\n");
                }
                config.append("  }\n");
            }
        }

        for (String domain : mappingsByDomain.keySet()) {
            config.append("  server {\n");
            config.append(getCodeForServerConfig());
            config.append("    listen "+nginx.getPort()+";\n");
            config.append("    server_name "+domain+";\n");
            boolean hasRoot = false;

            // set up SSL
            ProxySslConfig localSslConfig = null;
            for (UrlMapping mappingInDomain : mappingsByDomain.get(domain)) {
                ProxySslConfig sslConfig = mappingInDomain.getConfig(UrlMapping.SSL_CONFIG);
                if (sslConfig!=null) {
                    verifyConfig(sslConfig);
                    if (localSslConfig!=null) {
                        if (localSslConfig.equals(sslConfig)) {
                            //ignore identical config specified on multiple mappings
                        } else {
                            LOG.warn("{} mapping {} provides SSL config for {} when a different config had already been provided by another mapping, ignoring this one",
                                    new Object[] {this, mappingInDomain, domain});
                        }
                    } else if (globalSslConfig!=null) {
                        if (globalSslConfig.equals(sslConfig)) {
                            //ignore identical config specified on multiple mappings
                        } else {
                            LOG.warn("{} mapping {} provides SSL config for {} when a different config had been provided at root nginx scope, ignoring this one",
                                    new Object[] {this, mappingInDomain, domain});
                        }
                    } else {
                        //new config, is okay
                        localSslConfig = sslConfig;
                    }
                }
            }
            if (localSslConfig != null) {
                appendSslConfig(domain, config, "    ", localSslConfig, true, true);
            }

            for (UrlMapping mappingInDomain : mappingsByDomain.get(domain)) {
                // TODO Currently only supports "~" for regex. Could add support for other options,
                // such as "~*", "^~", literals, etc.
                boolean isRoot = mappingInDomain.getPath()==null || mappingInDomain.getPath().length()==0 || mappingInDomain.getPath().equals("/");
                if (isRoot && hasRoot) {
                    LOG.warn(""+this+" mapping "+mappingInDomain+" provides a duplicate / proxy, ignoring");
                } else {
                    hasRoot |= isRoot;
                    String location = isRoot ? "/" : "~ " + mappingInDomain.getPath();
                    config.append("    location "+location+" {\n");
                    Collection<UrlRewriteRule> rewrites = mappingInDomain.getConfig(UrlMapping.REWRITES);
                    if (rewrites != null && rewrites.size() > 0) {
                        for (UrlRewriteRule rule: rewrites) {
                            config.append("      rewrite \"^"+rule.getFrom()+"$\" \""+rule.getTo()+"\"");
                            if (rule.isBreak()) config.append(" break");
                            config.append(" ;\n");
                        }
                    }
                    config.append("      proxy_pass "+
                        (localSslConfig != null && localSslConfig.getTargetIsSsl() ? "https" :
                         (localSslConfig == null && globalSslConfig != null && globalSslConfig.getTargetIsSsl()) ? "https" :
                         "http")+
                        "://"+mappingInDomain.getUniqueLabel()+" ;\n");
                    config.append("    }\n");
                }
            }
            if (!hasRoot) {
                //provide a root block giving 404 if there isn't one for this server
                config.append("    location / { \n"+getCodeFor404()+"    }\n");
            }
            config.append("  }\n");
        }

        config.append("}\n");

        return config.toString();
    }

    protected String getCodeForServerConfig() {
        // See http://wiki.nginx.org/HttpProxyModule
        return ""+
            // this prevents nginx from reporting version number on error pages
            "    server_tokens off;\n"+

            // this prevents nginx from using the internal proxy_pass codename as Host header passed upstream.
            // Not using $host, as that causes integration test to fail with a "connection refused" testing
            // url-mappings, at URL "http://localhost:${port}/atC0" (with a trailing slash it does work).
            "    proxy_set_header Host $http_host;\n"+

            // following added, as recommended for wordpress in:
            // http://zeroturnaround.com/labs/wordpress-protips-go-with-a-clustered-approach/#!/
            "    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n"+
            "    proxy_set_header X-Real-IP $remote_addr;\n";
    }

    protected String getCodeFor404() {
        return "    return 404;\n";
    }

    protected void verifyConfig(ProxySslConfig proxySslConfig) {
          if(Strings.isEmpty(proxySslConfig.getCertificateDestination()) && Strings.isEmpty(proxySslConfig.getCertificateSourceUrl())){
            throw new IllegalStateException("ProxySslConfig can't have a null certificateDestination and null certificateSourceUrl. One or both need to be set");
        }
    }

    protected boolean appendSslConfig(String id, StringBuilder out, String prefix, ProxySslConfig ssl,
                                   boolean sslBlock, boolean certificateBlock) {
        if (ssl == null) return false;
        if (sslBlock) {
            out.append(prefix);
            out.append("ssl on;\n");
        }
        if (ssl.getReuseSessions()) {
            out.append(prefix);
            out.append("");
        }
        if (certificateBlock) {
            String cert;
            if (Strings.isEmpty(ssl.getCertificateDestination())) {
                cert = "" + id + ".crt";
            } else {
                cert = ssl.getCertificateDestination();
            }

            out.append(prefix);
            out.append("ssl_certificate " + cert + ";\n");

            String key;
            if (!Strings.isEmpty(ssl.getKeyDestination())) {
                key = ssl.getKeyDestination();
            } else if (!Strings.isEmpty(ssl.getKeySourceUrl())) {
                key = "" + id + ".key";
            } else {
                key = null;
            }

            if (key != null) {
                out.append(prefix);
                out.append("ssl_certificate_key " + key + ";\n");
            }
        }
        return true;
    }

}
