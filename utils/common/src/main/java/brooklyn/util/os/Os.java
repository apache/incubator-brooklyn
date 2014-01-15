package brooklyn.util.os;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import brooklyn.util.net.Urls;

public class Os {

    /** returns the /tmp dir, based on java.io.tmpdir but ignoring it if it's weird
     * (e.g. /var/folders/q2/363yynwx5lb_qpch1km2xvr80000gn/T/) and /tmp exists */
    public static String tmp() {
        String tmpdir = System.getProperty("java.io.tmpdir");
        if (tmpdir.contains("/var/") && new File("/tmp").exists())
            return "/tmp";
        return tmpdir;
    }

    public static String user() {
        return System.getProperty("user.name");
    }

    /** merges paths using forward slash (unix way); see {@link Urls#mergePaths(String...)} */
    public static String mergePathsUnix(String ...items) {
        return Urls.mergePaths(items);
    }

    /** merges paths using the local file separator */
    public static String mergePaths(String ...items) {
        char separatorChar = File.separatorChar;
        StringBuilder result = new StringBuilder();
        for (String item: items) {
            if (item.isEmpty()) continue;
            if (result.length() > 0 && result.charAt(result.length()-1) != separatorChar) result.append(separatorChar);
            result.append(item);
        }
        return result.toString();
    }

    public static boolean tryDeleteDirectory(String dir) {
        try {
            FileUtils.deleteDirectory(new File(dir));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // TODO migrate static OS-things from ResourceUtils to here!

}
