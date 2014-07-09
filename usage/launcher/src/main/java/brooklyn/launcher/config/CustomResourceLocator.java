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
package brooklyn.launcher.config;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigMap;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.os.Os;

/** class which allows non-standard locators to be registered for URL's being loaded */
public class CustomResourceLocator {

    private static final Logger log = LoggerFactory.getLogger(CustomResourceLocator.class);
    
    protected final ResourceUtils r;
    private ConfigMap config;

    public interface ResourceLocator {
        public boolean isApplicable(String url, ConfigMap config);
        public InputStream locate(String url, ConfigMap config, ResourceUtils r);
    }
    
    private static List<ResourceLocator> locators = new ArrayList<ResourceLocator>();
    
    public CustomResourceLocator(ConfigMap config, ResourceUtils r) {
        this.config = config;
        this.r = r;
    }
    
    public static void registerAlternateLocator(ResourceLocator locator) {
        locators.add(0, locator);
    }
    
    /** returns the first known locator for the given url/config pair */
    public static ResourceLocator getLocatorFor(String url, ConfigMap config) {
        for (ResourceLocator l: locators) {
            if (l.isApplicable(url, config)) return l;
        }
        return null;
    }
    
    /** finds the file indicated at the URL, using some rewrites if necessary to work around some known issues.
     * <p>
     * in particular, eclipse often does not copy WAR files as instructed by maven, so brooklyn.war might not be found */
    public InputStream getResourceFromUrl(String url) {
        // TODO we could allow the source to be overridden from config,
        // by allowing configuration e.g.
        // brooklyn.path.override.brooklyn.war=classpath://brooklyn-replacement-webapp.war
        // (not sure if this is a good idea or not)
        
        try {
            return r.getResourceFromUrl(url);
        } catch (Exception e) {
            ResourceLocator locator = getLocatorFor(url, config);
            if (locator!=null) {
                log.debug("Unable to load resource from "+url+"; attempting with locator "+locator);
                try {
                    InputStream result = locator.locate(url, config, r);
                    if (result!=null) return result;
                    if (result==null)
                        log.warn("Unable to load resource from "+url+", even with custom locator; rethrowing original exception");
                } catch (Exception e2) {
                    log.warn("Unable to load resource from "+url+", even with custom locator; rethrowing original exception, new exception is: "+e2);
                }
            }
            throw Exceptions.propagate(e);
        }
    }

    public static class SearchingClassPathInDevMode implements ResourceLocator {
        private final String urlToSearchFor;
        private final String classpathSuffixToSearchFor;
        private final String classpathSuffixToUse;

        public SearchingClassPathInDevMode(String urlToSearchFor, String classpathSuffixToSearchFor, String classpathSuffixToUse) {
            this.urlToSearchFor = urlToSearchFor;
            this.classpathSuffixToSearchFor = Os.nativePath(classpathSuffixToSearchFor);
            this.classpathSuffixToUse = classpathSuffixToUse;
        }
        
        @Override
        public boolean isApplicable(String url, ConfigMap config) {
            return config.getConfig(BrooklynDevelopmentModes.BROOKLYN_DEV_MODE).isEnabled()
                    && urlToSearchFor.equals(url);
        }

        @Override
        public InputStream locate(String url, ConfigMap config, ResourceUtils r) {
            String cp = System.getProperty("java.class.path");
            int cpi = cp.indexOf(classpathSuffixToSearchFor);
            if (cpi==-1) return null;
            String path = cp.substring(0, cpi);
            int lps = path.lastIndexOf(File.pathSeparatorChar);
            if (lps>=0) path = path.substring(lps+1);
            path = path + classpathSuffixToUse;
            log.debug("Looking for "+url+" in revised location "+path);
            InputStream result = r.getResourceFromUrl(path);
            log.info("Using "+url+" from revised location "+path);
            return result;
        }
    }
    
}
