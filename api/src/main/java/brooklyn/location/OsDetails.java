package brooklyn.location;

import javax.annotation.Nullable;

public interface OsDetails {

    /** The name of the operating system, e.g. "Debian" or "Red Hat Enterprise Linux Server" */
    @Nullable
    String getName();

    /**
     * The version of the operating system. Generally numeric (e.g. "6.3") but occasionally
     * alphabetic (e.g. Debian's "Squeeze").
     */
    @Nullable
    String getVersion();

    /** The operating system's architecture, e.g. "x86" or "x86_64" */
    @Nullable
    String getArch();

    boolean is64bit();

    boolean isWindows();
    boolean isLinux();
    boolean isMac();

}
