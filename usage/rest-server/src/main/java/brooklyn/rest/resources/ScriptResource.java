package brooklyn.rest.resources;

import brooklyn.rest.api.ScriptApi;
import brooklyn.rest.domain.ScriptExecutionSummary;
import brooklyn.util.stream.ThreadLocalPrintStream;
import brooklyn.util.stream.ThreadLocalPrintStream.OutputCapturingContext;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;

public class ScriptResource extends AbstractBrooklynRestResource implements ScriptApi {

    private static final Logger log = LoggerFactory.getLogger(ScriptResource.class);
    
    public static final String USER_DATA_MAP_SESSION_ATTRIBUTE = "brooklyn.script.groovy.user.data";
    public static final String USER_LAST_VALUE_SESSION_ATTRIBUTE = "brooklyn.script.groovy.user.last";
    
    @SuppressWarnings("rawtypes")
    @Override
    public ScriptExecutionSummary groovy(HttpServletRequest request, String script) {
        log.info("Web REST executing user-supplied script");
        if (log.isDebugEnabled()) {
            log.debug("Web REST user-supplied script contents:\n"+script);
        }
        
        Binding binding = new Binding();
        binding.setVariable("mgmt", mgmt());
        
        HttpSession session = request!=null ? request.getSession() : null;
        if (session!=null) {
            Map data = (Map) session.getAttribute(USER_DATA_MAP_SESSION_ATTRIBUTE);
            if (data==null) {
                data = new LinkedHashMap();
                session.setAttribute(USER_DATA_MAP_SESSION_ATTRIBUTE, data);
            }
            binding.setVariable("data", data);

            Object last = session.getAttribute(USER_LAST_VALUE_SESSION_ATTRIBUTE);
            binding.setVariable("last", last);
        }
        
        GroovyShell shell = new GroovyShell(binding);

        OutputCapturingContext stdout = ThreadLocalPrintStream.stdout().capture();
        OutputCapturingContext stderr = ThreadLocalPrintStream.stderr().capture();

        Object value = null;
        Throwable problem = null;
        try {
            value = shell.evaluate(script);
            if (session!=null)
                session.setAttribute(USER_LAST_VALUE_SESSION_ATTRIBUTE, value);
        } catch (Throwable t) {
            problem = t;
        } finally {
            stdout.end();
            stderr.end();
        }

        if (log.isDebugEnabled()) {
            log.debug("Web REST user-supplied script completed:\n"+
                    (value!=null ? "RESULT: "+value.toString()+"\n" : "")+ 
                    (problem!=null ? "ERROR: "+problem.toString()+"\n" : "")+
                    (!stdout.isEmpty() ? "STDOUT: "+stdout.toString()+"\n" : "")+
                    (!stderr.isEmpty() ? "STDERR: "+stderr.toString()+"\n" : ""));
        }

        // call toString on the result, in case it is not serializable
        return new ScriptExecutionSummary(
                value!=null ? value.toString() : null, 
                        problem!=null ? problem.toString() : null,
                                stdout.toString(), stderr.toString());
    }

}
