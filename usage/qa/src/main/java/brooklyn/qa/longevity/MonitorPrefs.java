package brooklyn.qa.longevity;

import java.io.File;
import java.net.URL;

import com.google.common.base.Objects;
import com.google.common.collect.Range;

public class MonitorPrefs {

    public URL webUrl;
    public int brooklynPid;
    public File logFile;
    public String logGrep;
    public File logGrepExclusionsFile;
    public String webProcessesRegex;
    public Range<Integer> numWebProcesses;
    public int webProcessesCyclingPeriod;
    public File outFile;
    public boolean abortOnError;
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("webUrl", webUrl)
                .add("brooklynPid", brooklynPid)
                .add("logFile", logFile)
                .add("logGrep", logGrep)
                .add("logGrepExclusionsFile", logGrepExclusionsFile)
                .add("outFile", outFile)
                .add("webProcessesRegex", webProcessesRegex)
                .add("numWebProcesses", numWebProcesses)
                .add("webProcessesCyclingPeriod", webProcessesCyclingPeriod)
                .toString();
    }
}
