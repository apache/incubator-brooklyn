package brooklyn;

import static com.google.common.base.Preconditions.checkNotNull;
import java.io.IOException;
import static java.lang.String.format;
import java.util.Properties;

public class BrooklynVersion {

  private static final String VERSION_RESOURCE_FILE = "META-INF/maven/io.brooklyn/brooklyn-core/pom.properties";

  private static final String VERSION_PROPERTY_NAME = "version";

  private static final BrooklynVersion INSTANCE = new BrooklynVersion();

  private final String version;

  public BrooklynVersion() {
    this.version = readVersionPropertyFromClasspath(BrooklynVersion.class.getClassLoader());
  }

  private String readVersionPropertyFromClasspath(ClassLoader resourceLoader) {
    Properties versionProperties = new Properties();
    try {
      versionProperties.load(checkNotNull(resourceLoader.getResourceAsStream(VERSION_RESOURCE_FILE), VERSION_RESOURCE_FILE));
    } catch (IOException exception) {
      throw new IllegalStateException(format("Unable to load version resource file '%s'", VERSION_RESOURCE_FILE), exception);
    }
    return checkNotNull(versionProperties.getProperty(VERSION_PROPERTY_NAME), VERSION_PROPERTY_NAME);
  }

  public static String get() {
    return INSTANCE.version;
  }

}
