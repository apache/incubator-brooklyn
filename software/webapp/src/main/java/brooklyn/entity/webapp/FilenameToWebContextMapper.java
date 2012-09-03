package brooklyn.entity.webapp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilenameToWebContextMapper {

    public static final Logger log = LoggerFactory.getLogger(FilenameToWebContextMapper.class);
    
    public String findArchiveNameFromUrl(String url, boolean verbose) {
        String name = url.substring(url.lastIndexOf('/') + 1);
        if (name.indexOf("?")>0) {
            Pattern p = Pattern.compile("[A-Za-z0-9_\\-]+\\..(ar|AR)($|(?=[^A-Za-z0-9_\\-]))");
            Matcher wars = p.matcher(name);
            if (wars.find()) {
                // take first such string
                name = wars.group();
                if (wars.find()) {
                    if (verbose) log.warn("Not clear which archive to deploy for "+url+": using "+name);
                } else {
                    if (verbose) log.info("Inferred archive to deploy for "+url+": using "+name);
                }
            } else {
                if (verbose) log.warn("Not clear which archive to deploy for "+url+": using "+name);
            }
        }
        return name;
    }

    public String convertDeploymentTargetNameToFilename(String targetName) {
        String result = targetName;
        if (result.isEmpty()) return "";
        if (targetName.startsWith("/")) {
            // treat input as a context
            result = result.substring(1);
            if (result.length()==0) result="ROOT";
            result += ".war";
        } else {
            // treat input as a file, unless it has no dots in it
            if (result.indexOf('.')==-1) result += ".war";
        }
        return result;
    }
    
    public String convertDeploymentTargetNameToContext(String targetName) {
        String result = targetName;
        if (result.isEmpty()) return "";
        if (targetName.startsWith("/")) {
            // treat input as a context - noop
        } else {
            // make it look like a context
            result = "/"+result;
            if (result.indexOf('.')==-1) {
                // no dot means no more processing
            } else {
                // look at extension
                String extension = result.substring(result.lastIndexOf('.')+1).toUpperCase();
                if (extension.matches(".AR")) {
                    // looks like it was a WAR/EAR/etc
                    result = result.substring(0, result.length()-4);
                    if (result.equalsIgnoreCase("/ROOT")) result = "/"; 
                } else {
                    // input didn't look like a war filename, no more processing
                }
            }
        }
        return result;        
    }

}
