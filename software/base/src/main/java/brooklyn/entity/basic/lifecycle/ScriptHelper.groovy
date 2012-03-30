package brooklyn.entity.basic.lifecycle;

import java.util.List

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class ScriptHelper {

	public static final Logger log = LoggerFactory.getLogger(ScriptHelper.class);
			
	protected final ScriptRunner runner;
	protected final String summary;
	
	public final ScriptPart header = [this];
	public final ScriptPart body = [this];
	public final ScriptPart footer = [this];
	
	protected Closure resultCodeCheck = { true }
	protected Closure executionCheck = { true }
	
	public ScriptHelper(ScriptRunner runner, String summary) {
		this.runner = runner;
		this.summary = summary;
	}

	/** takes a closure which accepts this ScriptHelper,
	 * returns true or false as to whether the script needs to run
	 * (or can throw error if desired) */
	public ScriptHelper executeIf(Closure c) {
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
	 * convenience for error-checking the result
	 * <p> 
	 * takes closure which accepts bash exit code (integer),
	 * and returns false if it is invalid; 
	 * default is that this resultCodeCheck closure always returns true 
	 * (and the exit code is made available to the caller if they care) */
	public ScriptHelper requireResultCode(Closure integerFilter) {
		resultCodeCheck = integerFilter
		this		
	}
	
	public int execute() {
		if (!executionCheck.call(this)) return 0
		if (log.isDebugEnabled()) log.debug "executing: {} - {}", summary, lines
		int result;
		try {
			result = runner.execute(lines, summary)
		} catch (InterruptedException e) {
			throw e
		} catch (Exception e) {
			throw new IllegalStateException("execution failed, invocation error for ${summary}", e)
		}
		if (log.isDebugEnabled()) log.debug "finished executing: {} - result code {}", summary, result
		if (!resultCodeCheck.call(result))
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
	/** passes the list to a closure for amendment; result of closure ignored */
	public ScriptHelper apply(Closure c) {
		c.call(lines)
		helper
	}
	public boolean isEmpty() { lines.isEmpty() }
}

public class CommonCommands {
    /** returns a string for checking whether the given executable is available,
     * and installing it if necessary, using {@link #installPackage}
     * and accepting the same flags e.g. for apt, yum, rpm */
    public static String installExecutable(Map flags=[:], String executable) {
        "which ${executable} || "+installPackage(flags, executable)
    }
    /** returns a string for installing the given package;
     * flags can contain common overrides e.g. for apt, yum, rpm
     * (as the package names can be different for each of those), e.g.:
     * installPackage("libssl-devel", yum: "openssl-devel", apt:"openssl libssl-dev zlib1g-dev");
     * exit code 44 used to indicate failure */
    public static String installPackage(Map flags=[:], String packageDefaultName) {
        "(which apt-get && apt-get install -y ${flags.apt?:packageDefaultName}) || "+
                "(which rpm && rpm -i ${flags.rpm?:packageDefaultName}) || "+
                "(which yum && yum -y install ${flags.yum?:packageDefaultName}) || "+
                //FIXME does this actually exit? or just exit from this subshell
                "(echo \"WARNING: no known package manager to install ${packageDefaultName}, may fail subsequently\")"
    }
    public static final String INSTALL_TAR = installExecutable("tar");
    public static final String INSTALL_CURL = installExecutable("curl");
    public static final String INSTALL_WGET = installExecutable("wget");
    
    /** returns string for downloading from a url and saving to a file;
     * currently using curl
     * <p>
     * will read from a local repository, if files have been copied there
     * (cp -r /tmp/brooklyn/installs/ ~/.brooklyn/repository/),
     * unless skipLocalRepo: true
     * <p>
     * ideally use a blobstore staging area
     */
    public static List<String> downloadUrlAs(Map flags=[:], String url, String entityVersionPath, String pathlessFilenameToSaveAs) {
        boolean useLocalRepo = flags.skipLocalRepo!=null ? !flags.skipLocalRepo : true;
        String command = "curl -L \"${url}\" -o ${pathlessFilenameToSaveAs}";
        if (useLocalRepo) {
            String file = '$'+"HOME/.brooklyn/repository/${entityVersionPath}/${pathlessFilenameToSaveAs}";
            command = "if [ -f ${file} ]; then cp ${file} ./${pathlessFilenameToSaveAs}; else "+command+" ; fi"
        }
        command = command + " || exit 9"
        return [INSTALL_CURL, command];
    }
}
