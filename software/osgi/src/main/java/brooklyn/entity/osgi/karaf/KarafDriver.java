package brooklyn.entity.osgi.karaf;

import java.io.File;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface KarafDriver extends JavaSoftwareProcessDriver {

    public String getRunDir();

    public void copyFile(File src, File destination);
}
