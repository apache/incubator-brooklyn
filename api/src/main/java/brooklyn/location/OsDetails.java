package brooklyn.location;

public interface OsDetails {

    boolean isWindows();
    boolean isLinux();
    boolean isMac();
    
    String getName();
    String getArch();
    String getVersion();
    boolean is64bit();
}
