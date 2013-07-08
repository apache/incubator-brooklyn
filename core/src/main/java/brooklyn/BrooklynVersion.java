package brooklyn;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class BrooklynVersion {

  private static final String VERSION_RESOURCE_FILE = "META-INF/maven/io.brooklyn/brooklyn-core/pom.properties";

  private static final String VERSION_PROPERTY_NAME = "version";

  private static final BrooklynVersion INSTANCE = new BrooklynVersion();

  private final String versionFromClasspath;
  // static useful when running from the IDE
  private final String versionFromStatic = "0.6.0-SNAPSHOT"; // BROOKLYN_VERSION
  private final String version;

  public BrooklynVersion() {
    this.versionFromClasspath = readVersionPropertyFromClasspath(BrooklynVersion.class.getClassLoader());
    this.version = versionFromClasspath==null ? versionFromStatic : versionFromClasspath;
  }

  private String readVersionPropertyFromClasspath(ClassLoader resourceLoader) {
    Properties versionProperties = new Properties();
    try {
      InputStream versionStream = resourceLoader.getResourceAsStream(VERSION_RESOURCE_FILE);
      if (versionStream==null) return null;
      versionProperties.load(checkNotNull(versionStream));
    } catch (IOException exception) {
      throw new IllegalStateException(format("Unable to load version resource file '%s'", VERSION_RESOURCE_FILE), exception);
    }
    return checkNotNull(versionProperties.getProperty(VERSION_PROPERTY_NAME), VERSION_PROPERTY_NAME);
  }

  public static String get() {
    return INSTANCE.version;
  }

}
