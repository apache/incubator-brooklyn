package brooklyn.entity.network.bind;

import java.io.File;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface BindDnsServerDriver extends JavaSoftwareProcessDriver {

    public String getRunDir();

}
