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
package brooklyn;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.osgi.Osgis;
import brooklyn.util.osgi.Osgis.ManifestHelper;
import brooklyn.util.text.Strings;

/**
 * Wraps the version of Brooklyn.
 * <p>
 * Also retrieves the SHA-1 from any OSGi bundle, and checks that the maven and osgi versions match.
 */
public class BrooklynVersion {

  private static final Logger log = LoggerFactory.getLogger(BrooklynVersion.class);
  
  private static final String MVN_VERSION_RESOURCE_FILE = "META-INF/maven/org.apache.brooklyn/brooklyn-core/pom.properties";
  private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
  private static final String BROOKLYN_CORE_SYMBOLIC_NAME = "org.apache.brooklyn.core";
  
  private static final String MVN_VERSION_PROPERTY_NAME = "version";
  private static final String OSGI_VERSION_PROPERTY_NAME = Attributes.Name.IMPLEMENTATION_VERSION.toString();
  private static final String OSGI_SHA1_PROPERTY_NAME = "Implementation-SHA-1";


  private final static String VERSION_FROM_STATIC = "0.7.0-SNAPSHOT"; // BROOKLYN_VERSION
  private static final AtomicReference<Boolean> IS_DEV_ENV = new AtomicReference<Boolean>();
  
  public static final BrooklynVersion INSTANCE = new BrooklynVersion();
  
  private final Properties versionProperties = new Properties();
  
  public BrooklynVersion() {
      // we read the maven pom metadata and osgi metadata and make sure it's sensible
      // everything is put into a single map for now (good enough, but should be cleaned up)
      readPropertiesFromMavenResource(BrooklynVersion.class.getClassLoader());
      readPropertiesFromOsgiResource(BrooklynVersion.class.getClassLoader(), BROOKLYN_CORE_SYMBOLIC_NAME);
      // TODO there is also build-metadata.properties used in ServerResource /v1/server/version endpoint
      // see comments on that about folding it into this class instead

      checkVersions();
  }

  public void checkVersions() {
      String mvnVersion = getVersionFromMavenProperties();
      if (mvnVersion!=null && !VERSION_FROM_STATIC.equals(mvnVersion)) {
          throw new IllegalStateException("Version error: maven "+mvnVersion+" / code "+VERSION_FROM_STATIC);
      }
      
      String osgiVersion = versionProperties.getProperty(OSGI_VERSION_PROPERTY_NAME);
      // TODO does the OSGi version include other slightly differ gubbins/style ?
      if (osgiVersion!=null && !VERSION_FROM_STATIC.equals(osgiVersion)) {
          throw new IllegalStateException("Version error: osgi "+osgiVersion+" / code "+VERSION_FROM_STATIC);
      }
  }

  /** Returns version as inferred from classpath/osgi, if possible, or 0.0.0-SNAPSHOT.
   * See also {@link #getVersionFromMavenProperties()} and {@link #getVersionFromOsgiManifest()}.
   * @deprecated since 0.7.0, in favour of the more specific methods (and does anyone need that default value?)
   */
  @Deprecated
  public String getVersionFromClasspath() {
      String v = getVersionFromMavenProperties();
      if (Strings.isNonBlank(v)) return v;
      v = getVersionFromOsgiManifest();
      if (Strings.isNonBlank(v)) return v;
      return "0.0.0-SNAPSHOT";
  }
  
  @Nullable
  public String getVersionFromMavenProperties() {
      return versionProperties.getProperty(MVN_VERSION_PROPERTY_NAME);
  }

  @Nullable
  public String getVersionFromOsgiManifest() {
      return versionProperties.getProperty(OSGI_VERSION_PROPERTY_NAME);
  }
  
  @Nullable
  /** SHA1 of the last commit to brooklyn at the time this build was made.
   * For SNAPSHOT builds of course there may have been further non-committed changes. */
  public String getSha1FromOsgiManifest() {
      return versionProperties.getProperty(OSGI_SHA1_PROPERTY_NAME);
  }
  
  public String getVersion() {
    return VERSION_FROM_STATIC;
  }
  
  public boolean isSnapshot() {
      return (getVersion().indexOf("-SNAPSHOT")>=0);
  }
    
  private void readPropertiesFromMavenResource(ClassLoader resourceLoader) {
    try {
      InputStream versionStream = resourceLoader.getResourceAsStream(MVN_VERSION_RESOURCE_FILE);
      if (versionStream==null) {
          if (isDevelopmentEnvironment()) {
              // allowed for dev env
              log.trace("No maven resource file "+MVN_VERSION_RESOURCE_FILE+" available");
          } else {
              log.warn("No maven resource file "+MVN_VERSION_RESOURCE_FILE+" available");
          }
          return;
      }
      versionProperties.load(checkNotNull(versionStream));
    } catch (IOException e) {
      log.warn("Error reading maven resource file "+MVN_VERSION_RESOURCE_FILE+": "+e, e);
    }
  }

  /** reads properties from brooklyn-core's manifest */
  private void readPropertiesFromOsgiResource(ClassLoader resourceLoader, String symbolicName) {
      Enumeration<URL> paths;
      try {
          paths = BrooklynVersion.class.getClassLoader().getResources(MANIFEST_PATH);
      } catch (IOException e) {
          // shouldn't happen
          throw Exceptions.propagate(e);
      }
      while (paths.hasMoreElements()) {
          URL u = paths.nextElement();
          try {
              ManifestHelper mh = Osgis.ManifestHelper.forManifest(u.openStream());
              if (BROOKLYN_CORE_SYMBOLIC_NAME.equals(mh.getSymbolicName())) {
                  Attributes attrs = mh.getManifest().getMainAttributes();
                  for (Object key: attrs.keySet()) {
                      // key is an Attribute.Name; toString converts to string
                      versionProperties.put(key.toString(), attrs.getValue(key.toString()));
                  }
                  return;
              }
          } catch (Exception e) {
              Exceptions.propagateIfFatal(e);
              log.warn("Error reading OSGi manifest from "+u+" when determining version properties: "+e, e);
          }
      }
      if (isDevelopmentEnvironment()) {
          // allowed for dev env
          log.trace("No OSGi manifest available to determine version properties");
      } else {
          log.warn("No OSGi manifest available to determine version properties");
      }
  }

  /** 
   * Returns whether this is a Brooklyn dev environment,
   * specifically core/target/classes/ is on the classpath for the org.apache.brooklyn.core project.
   * <p>
   * In a packaged or library build of Brooklyn (normal usage) this should return false,
   * and all OSGi components should be available.
   */
  public static boolean isDevelopmentEnvironment() {
      Boolean isDevEnv = IS_DEV_ENV.get();
      if (isDevEnv!=null) return isDevEnv;
      synchronized (IS_DEV_ENV) {
          isDevEnv = computeIsDevelopmentEnvironment();
          IS_DEV_ENV.set(isDevEnv);
          return isDevEnv;
      }
  }
  
  private static boolean computeIsDevelopmentEnvironment() {
      Enumeration<URL> paths;
      try {
          paths = BrooklynVersion.class.getClassLoader().getResources("brooklyn/BrooklynVersion.class");
      } catch (IOException e) {
          // shouldn't happen
          throw Exceptions.propagate(e);
      }
      while (paths.hasMoreElements()) {
          URL u = paths.nextElement();
          if (u.getPath().endsWith("core/target/classes/brooklyn/BrooklynVersion.class")) {
              try {
                  log.debug("Brooklyn debug environment detected; BrooklynVersion class is at: "+u);
                  return true;
              } catch (Exception e) {
                  Exceptions.propagateIfFatal(e);
                  log.warn("Error reading manifest to determine whether this is a development environment: "+e, e);
              }
          }
      }
      return false;
  }

  public void logSummary() {
      log.debug("Brooklyn version "+getVersion()+" (git SHA1 "+getSha1FromOsgiManifest()+")");
  }

  /** @deprecated since 0.7.0, redundant with {@link #get()} */ @Deprecated
  public static String getVersionFromStatic() {
      return VERSION_FROM_STATIC;
  }

  public static String get() {
      return INSTANCE.getVersion();
  }
  
}
