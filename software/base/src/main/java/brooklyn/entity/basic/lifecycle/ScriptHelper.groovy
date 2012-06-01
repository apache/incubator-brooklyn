package brooklyn.entity.basic.lifecycle;

import java.util.List

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.base.Predicate
import com.google.common.base.Predicates;

import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.mutex.WithMutexes

public class ScriptHelper {

	public static final Logger log = LoggerFactory.getLogger(ScriptHelper.class);
			
	protected final ScriptRunner runner;
	protected final String summary;
	
	public final ScriptPart header = [this];
	public final ScriptPart body = [this];
	public final ScriptPart footer = [this];
	
	protected Predicate<Integer> resultCodeCheck = Predicates.alwaysTrue();
    protected Predicate<ScriptHelper> executionCheck = Predicates.alwaysTrue();
	
	public ScriptHelper(ScriptRunner runner, String summary) {
		this.runner = runner;
		this.summary = summary;
	}

	/**
	 * Takes a closure which accepts this ScriptHelper and returns true or false
	 * as to whether the script needs to run (or can throw error if desired)
	 */
	public ScriptHelper executeIf(Closure c) {
		return executeIf(GroovyJavaMethods.predicateFromClosure(c))
	}
    public ScriptHelper executeIf(Predicate<ScriptHelper> c) {
        executionCheck = c
        this
    }
    
	public ScriptHelper skipIfBodyEmpty() { executeIf { !it.body.isEmpty() } }
	public ScriptHelper failIfBodyEmpty() { executeIf {
		if (it.body.isEmpty()) throw new IllegalStateException("body empty for "+summary);
		true
	} }
	
	public ScriptHelper failOnNonZeroResultCode(boolean val=true) {
		requireResultCode( val? { it==0 } : { true } )
		this
	}
	/**
	 * Convenience for error-checking the result.
	 *
	 * Takes closure which accepts bash exit code (integer),
	 * and returns false if it is invalid. Default is that this resultCodeCheck
	 * closure always returns true (and the exit code is made available to the
	 * caller if they care)
	 */
	public ScriptHelper requireResultCode(Closure integerFilter) {
		return requireResultCode(GroovyJavaMethods.predicateFromClosure(integerFilter))
	}

    public ScriptHelper requireResultCode(Predicate<Integer> integerFilter) {
        resultCodeCheck = integerFilter
        this
    }

    protected Closure mutexAcquire = {}
    protected Closure mutexRelease = {}
    /** indicates that the script should acquire the given mutexId on the given mutexSupport 
     * and maintain it for the duration of script execution;
     * typically used to prevent parallel scripts from conflicting in access to a resource
     * (e.g. a folder, or a config file used by a process)
     */
    public ScriptHelper useMutex(WithMutexes mutexSupport, String mutexId, String description) {
        mutexAcquire = { mutexSupport.acquireMutex(mutexId, description); };
        mutexRelease = { mutexSupport.releaseMutex(mutexId); };
        return this;
    }
    
	public int execute() {
		if (!executionCheck.apply(this)) return 0
		if (log.isDebugEnabled()) log.debug "executing: {} - {}", summary, lines
		int result;
		try {
            mutexAcquire.call()
			result = runner.execute(lines, summary)
		} catch (InterruptedException e) {
			throw e
		} catch (Exception e) {
			throw new IllegalStateException("execution failed, invocation error for ${summary}", e)
		} finally {
            mutexRelease.call()
		}
		if (log.isDebugEnabled()) log.debug "finished executing: {} - result code {}", summary, result
		if (!resultCodeCheck.apply(result))
			throw new IllegalStateException("execution failed, invalid result ${result} for ${summary}")
		result		
	}
	
	public List<String> getLines() {
		header.lines+body.lines+footer.lines
	}
}

public class ScriptPart {
	protected ScriptHelper helper;
	protected List<String> lines = []
	
	public ScriptPart(ScriptHelper helper) {
		this.helper = helper;
	}

	public ScriptHelper append(String l1) {
		lines.add(l1);
        helper
	}
	//bit ugly but 'xxx' and "xxx" mixed don't fit String...
	public ScriptHelper append(Object l1, Object l2, Object ...ll) {
        append(l1);
        append(l2);
        ll.each { append it }
        helper
	}
	public ScriptHelper append(Collection ll) {
        ll.each { append it }
		helper
	}
	
	public ScriptHelper prepend(String l1) {
		lines.add(0, l1);
	}
	public ScriptHelper prepend(Object l1, Object l2, Object ...ll) {
        for (int i=ll.length-1; i>=0; i--) prepend(ll[i])
        prepend(l2);
        prepend(l1);

	}
	public ScriptHelper prepend(Collection ll) {
        List l = new ArrayList(ll);
        Collections.reverse(l);
        l.each { prepend it }
		helper
	}
	
	public ScriptHelper reset(String l1) {
		reset([l1])
	}
	public ScriptHelper reset(Object l1, Object l2, Object ...ll) {
        lines.clear();
        append(l1, l2, ll);
	}
	public ScriptHelper reset(List ll) {
		lines.clear()
		append(ll);
	}

	/** Passes the list to a closure for amendment; result of closure ignored. */
	public ScriptHelper apply(Closure c) {
		c.call(lines)
		helper
	}

	public boolean isEmpty() { lines.isEmpty() }
}

public class CommonCommands {

    /**
     * Returns a string for checking whether the given executable is available,
     * and installing it if necessary.
     * 
     * Uses {@link #installPackage} and accepts the same flags e.g. for apt, yum, rpm.
     */
    public static String installExecutable(Map flags=[:], String executable) {
        missing(executable, installPackage(flags, executable))
    }

    /** Returns a command with all output redirected to /dev/null */
    public static String quiet(String command) { "(${command} > /dev/null 2>&1)" }

    /** Returns a command that always exits successfully */
    public static String ok(String command) { "(${command} || true)" }

    /**
     * Returns a command for safely running as root, using {@code sudo}.
     * 
     * Ensuring non-blocking if password not set by using {@code -S} which reads
     * from stdin routed to {@code /dev/null} and {@code -E} passes the parent
     * environment in. If already root, simplem runs the command.
     */
    public static String sudo(String command) { "(test \$UID -eq 0 && ${command} || sudo -E -n ${command})" }

    /** Returns a command that runs only 1f the operating system is as specified; Checks {@code /etc/issue} for the specified name */
    public static String on(String osName, String command) { "(grep \"${osName}\" /etc/issue && ${command})" }

    /** Returns a command that runs only if the specified executable is in the path */
    public static String file(String path, String command) { "(test -f ${path} && ${command})" }

    /** Returns a command that runs only if the specified executable is in the path */
    public static String exists(String executable, String command) { "(which ${executable} && ${command})" }

    /** Returns a command that runs only if the specified executable is NOT in the path */
    public static String missing(String executable, String command) { "(which ${executable} || ${command})" }

    /** Returns a sequence of chained commands that runs until one of them fails */
    public static String chain(Collection commands) { "(" + commands.join(" && ") + ")" }

    /** Returns a sequence of alternative commands that runs until one of the commands succeeds */
    public static String alternatives(Collection commands, String failure) { "("  + commands.join(" || ") + " || ${failure})" }

    /**
     * Returns a command for installing the given package.
     *
     * Flags can contain common overrides for deb, apt, yum, rpm and port
     * as the package names can be different for each of those:
     * <pre>
     * installPackage("libssl-devel", yum:"openssl-devel", apt:"openssl libssl-dev zlib1g-dev")
     * </pre>
     */
    public static String installPackage(Map flags=[:], String packageDefaultName) {
        alternatives([
	            exists("dpkg", sudo("dpkg -i ${flags.deb?:packageDefaultName}")),
	            exists("apt-get", sudo("apt-get install -y ${flags.apt?:packageDefaultName}")),
	            exists("yum", sudo("yum -y install ${flags.yum?:packageDefaultName}")),
	            exists("rpm", sudo("rpm -i ${flags.rpm?:packageDefaultName}")),
	            exists("port", sudo("port install ${flags.port?:packageDefaultName}")) ],
	        "(echo \"WARNING: no known/successful package manager to install ${packageDefaultName}, may fail subsequently\")")
    }
    public static final String INSTALL_TAR = installExecutable("tar");
    public static final String INSTALL_CURL = installExecutable("curl");
    public static final String INSTALL_WGET = installExecutable("wget");

    /**
     * Returns command for downloading from a url and saving to a file;
     * currently using {@code curl}.
     *
     * Will read from a local repository, if files have been copied there
     * ({@code cp -r /tmp/brooklyn/installs/ ~/.brooklyn/repository/})
     * unless <em>skipLocalrepo</em> is {@literal true}.
     * <p>
     * Ideally use a blobstore staging area.
     */
    public static List<String> downloadUrlAs(Map flags=[:], String url, String entityVersionPath, String pathlessFilenameToSaveAs) {
        boolean useLocalRepo = flags.skipLocalRepo!=null ? !flags.skipLocalRepo : true;
        String command = "curl -L \"${url}\" -o ${pathlessFilenameToSaveAs}";
        if (useLocalRepo) {
            String file = "\$HOME/.brooklyn/repository/${entityVersionPath}/${pathlessFilenameToSaveAs}";
            command = "if [ -f ${file} ]; then cp ${file} ./${pathlessFilenameToSaveAs}; else ${command} ; fi"
        }
        command = command + " || exit 9"
        return [INSTALL_CURL, command];
    }
}
