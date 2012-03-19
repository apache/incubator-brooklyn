package brooklyn.location.basic;

import brooklyn.location.OsDetails;

public class BasicOsDetails implements OsDetails {

    final String name, arch, version;
    
    public BasicOsDetails(String name, String arch, String version) {
        this.name = name; this.arch = arch; this.version = version;
    }
    
    /** java property os.name (human readable name); e.g. "Mac OS X" */
    @Override
    public String getName() {
        return name;
    }
    /** java property os.arch (hardware architecture); e.g. "x86_64" */
    @Override
    public String getArch() {
        return arch;
    }
    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean isWindows() {
        //TODO confirm
        return getName()!=null && getName().toLowerCase().contains("microsoft");
    }

    @Override
    public boolean isLinux() {
        //TODO confirm
        return getName()!=null && getName().toLowerCase().contains("linux");
    }

    @Override
    public boolean isMac() {
        return getName()!=null && getName().equals(OsNames.MAC_OS_X);
    }
    
    @Override
    public String toString() {
        return "OS["+name+";"+arch+";"+version+"]";
    }

    public static class OsNames {
        public static final String MAC_OS_X = "Mac OS X";
    }
    
    public static class OsArchs {
        public static final String X_86_64 = "x86_64";
//        public static final String X_86 = "x86";
//        // is this standard?  or do we ever need the above?
        public static final String I386 = "i386";
    }

    public static class OsVersions {
        public static final String MAC_10_5 = "10.5";
        public static final String MAC_10_6 = "10.6";
    }
    
    public static class Factory {
        public OsDetails newLocalhostInstance() {
            return new BasicOsDetails(System.getProperty("os.name"), System.getProperty("os.arch"), System.getProperty("os.version"));
        }
        
        public static final OsDetails ANONYMOUS_LINUX = new BasicOsDetails("linux", OsArchs.I386, "unknown");
        public static final OsDetails ANONYMOUS_LINUX_64 = new BasicOsDetails("linux", OsArchs.X_86_64, "unknown");
    }
    
    public static void main(String[] args) {
        System.out.println("ARCH: "+new Factory().newLocalhostInstance().toString());
    }
}
