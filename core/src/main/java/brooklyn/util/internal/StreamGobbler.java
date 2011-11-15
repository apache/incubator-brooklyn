package brooklyn.util.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import org.slf4j.Logger;

public class StreamGobbler extends Thread {
	
    protected final InputStream stream;
    protected final PrintStream out;
    protected final Logger log;
    
    public StreamGobbler(InputStream stream, PrintStream out, Logger log) {
        this.stream = stream;
        this.out = out;
        this.log = log;
    }
    
    String prefix = "";
    public StreamGobbler setPrefix(String prefix) {
		this.prefix = prefix;
		return this;
	}
    
    public void run() {
        int c = -1;
        try {
            while ((c=stream.read())>=0) {
                onChar(c);
            }
            onClose();
        } catch (IOException e) {
        	onClose();
        	//TODO parametrise log level, for this error, and for normal messages
        	if (log!=null) log.debug(prefix+"exception reading from stream ("+e+")");
        }
    }
    
    StringBuffer lineSoFar = new StringBuffer("");
    public void onChar(int c) {
    	if (c=='\n') {
    		onLine(lineSoFar.toString());
    		lineSoFar.setLength(0);
    	} else {
    		lineSoFar.append((char)c);
    	}
    }
    
    public void onLine(String line) {
    	if (out!=null) out.println(prefix+line);
    	if (log!=null) log.info(prefix+line);
    }
    
    public void onClose() {
    	onLine(lineSoFar.toString());
		lineSoFar.setLength(0);
    }
    
}
