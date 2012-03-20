package brooklyn.location;

public interface OsDetails {

    boolean isWindows();
    boolean isLinux();
    boolean isMac();
    
    String getName();
    String getArch();
    String getVersion();
    
//    * <tr><td><code>os.name</code></td>
//    *     <td>Operating system name</td></tr>
//    * <tr><td><code>os.arch</code></td>
//    *     <td>Operating system architecture</td></tr>
//    * <tr><td><code>os.version</code></td>
//    *     <td>Operating system version</td></tr>

}
