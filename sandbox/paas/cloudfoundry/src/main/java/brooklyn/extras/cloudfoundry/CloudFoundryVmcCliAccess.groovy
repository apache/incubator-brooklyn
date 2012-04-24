package brooklyn.extras.cloudfoundry

import groovy.transform.EqualsAndHashCode

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.util.IdGenerator
import brooklyn.util.ResourceUtils
import brooklyn.util.ShellUtils
import brooklyn.util.mutex.WithMutexes

class CloudFoundryVmcCliAccess {
    
    private static final Logger log = LoggerFactory.getLogger(CloudFoundryVmcCliAccess.class)

    /** optional user-supplied context object used for classloading context and
     * inserting into toString to help with context */
    protected Object context = this;
    
    String appName, war, url, target;
    WithMutexes mutexSupport;
    
    String appPath;
    public synchronized String getAppPath() {
        if (this.@appPath) return this.@appPath;
        if (context in Entity) {
            appPath = "/tmp/brooklyn/apps/"+((Entity)context).application.id+"/staging/"+
                "cloudfoundry/"+((Entity)context).id+"/";
        } else {
            appPath = "/tmp/brooklyn/cloudfoundry/"+IdGenerator.makeRandomId(6)+"/"
        }
        return this.@appPath
    }
    
    protected def requiredFields = [];
    //    { requiredFields += ["target", "username", "password"] }

    protected validate() {
        requiredFields.each {
            if (this."${it}"==null) throw new NullPointerException("Flag '${it}' must be passed to constructor for "+this.getClass().getSimpleName());
        }
    }

    public String toString() {
        if (context==this || context==null) return "brooklyn:"+getClass().getSimpleName();
        return "CfVmc:"+context;
    }
    
    /** returns lines of the form PATH=/usr/bin:/usr/local/bin:.
     * for use passing to exec */
    protected static String[] getDefaultEnvironmentForExec() {
        System.getenv().collect { k,v -> "${k}=${v}" }         
    }

    protected String[] exec(String cmd) {
        ShellUtils.exec(cmd, log, context);
    }
    protected String[] exec(String cmd, String input) {
        ShellUtils.exec(cmd, input, log, context);
    }

    private Map<String,AppRecord> apps = null;
    public synchronized Map<String,AppRecord> getApps(boolean refresh=false) {
        if (refresh || apps==null) apps=_apps();
        return apps;
    }
    public AppRecord getAppRecord(String appName, boolean refresh=false) {
        getApps(refresh).get(appName);
    }
    public Collection<String> getAppNames(boolean refresh=false) {
        getApps(refresh).keySet();
    }
    @EqualsAndHashCode
    public static class AppRecord implements Serializable {
        String appName, state, url;
        int size;
        List services = []
        public static AppRecord parse(String line) {
            def fields = line.split("\\|");
            AppRecord result = new AppRecord(
                appName: fields[1].trim(),
                size: Integer.parseInt(fields[2].trim()),
                state: fields[3].trim(),
                url: fields[4].trim())
            for (String svc : fields[5].trim().split("\\s+")) {
                //add services (skip blank entries introduced by split)
                if (svc) result.services << svc
            }
            return result
        }
    }
    protected Map<String,AppRecord> _apps() {
        validate();
        Map result = [:]
        useTarget(target, "lookup apps") {
            String[] lines = exec("vmc apps");
            //        +---------------+----+---------+--------------------------------+-------------+
            //        | Application   | #  | Health  | URLS                           | Services    |
            //        +---------------+----+---------+--------------------------------+-------------+
            //        | hellobrooklyn | 1  | RUNNING | hellobrooklyn.cloudfoundry.com | mysql-8c1d0 |
            //        | hellobrookly2 | 1  | RUNNING | hellobrookly2.cloudfoundry.com | mysql-8c1d0 |
            //        +---------------+----+---------+--------------------------------+-------------+

            def li = lines.iterator();
            //skip 3 header lines; bail out if not enough (e.g. 'No Applications')
            if (!li.hasNext()) return result; else li.next();
            if (!li.hasNext()) return result; else li.next();
            if (!li.hasNext()) return result; else li.next();
            while (li.hasNext()) {
                String line = li.next();
                if (line.startsWith("+---"))
                    continue;
                def record = AppRecord.parse(line);
                result.put(record.appName, record);
            }
        }
        
        result
    }

    public String getAppName(Map localFlags) {
        String appName = localFlags.appName ?: this.@appName;
        if (appName) return appName;
        throw new NullPointerException("appName is required for ${context}"); 
    }

    public String getWar(Map localFlags) {
        String war = localFlags.war ?: this.@war;
        if (war) return war;
        throw new NullPointerException("war is required for this operation on ${context}");
    }

    public String getUrl(Map localFlags=[:]) {
        //not permitted on cloudfoundry.com; might work elsewhere
        String url = localFlags.url ?: this.@url;
        if (url) return url;
        return getAppRecord(getAppName(localFlags))?.url
    }

    /** sets the target location that will be used, e.g. api.cloudfoundry.com
     * <p>
     * we assume vmc on the local system has already been configured to log in to that endpoint,
     * so user+pass is not used. by default the last target set will be used.
     * <p>
     * this is not parallel-safe (if someone else switches target);
     * we compensate for that by always calling setTarget(target),
     * which is not safe by itself but if wrapped in a useTarget block
     * is safe unless something outside brooklyn retargets vmc at the same time. 
     * (a cleaner alternative would be to use a java API as described in issue #15.) 
     */
    public void setTarget(String target) {
        this.target = target;
        if (target) exec("vmc target ${target}")
    }
    
    public Object useTarget(String target, String description, Closure code) {
        boolean hasMutex = false;
        try {
            if (mutexSupport && !mutexSupport.hasMutex("vmc")) {
                mutexSupport.acquireMutex("vmc", "Cloud Foundry vmc target ${target} - "+description);
                hasMutex = true;
            }
            setTarget(target);
            return code.call();
        } finally {
            if (hasMutex) mutexSupport.releaseMutex("vmc");
        }
    }
    
    public String getTarget() { target }
    
    /** flags appName and war (URL of deployable resource) required;
     * memory (eg "512M") and url (target url) optional
     */
    public AppRecord runAppWar(Map flags=[:]) {
        Collection apps = getAppNames();

        String appName = getAppName(flags);
        String appPath = getAppPath();
        new File(appPath).mkdirs();
        new File(appPath+"/root.war") << new ResourceUtils(context).getResourceFromUrl(getWar(flags));
        
        useTarget(target, "run ${appName} WAR") {
            if (apps.contains(appName)) {
                //update

                //stop done implicitly on server
                //            exec("vmc stop ${appName}")

                if (flags.memory) exec("vmc mem ${appName} "+flags.memory);
                if (flags.url) {
                    url = getUrl(flags)
                    exec("vmc map ${appName} "+url);
                }

                exec("vmc update ${appName} --path ${appPath}");

                exec("vmc start ${appName}")
            } else {
                //create
                String memory = flags.memory ?: "512M";
                url = getUrl(flags)
                exec("vmc push"+
                        " ${appName}"+
                        (url ? " --url ${url}" : "")+
                        " --path ${appPath}"+
                        //" --runtime java"+  //what is syntax here?  vmc runtimes shows java; frameworks shows java_web; all seem to prompt
                        " --mem 512M",
                        //need CR supplied twice (java prompt, and services prompt) since can't seem to get it specified on CLI;
                        //and once more if url is default
                        "\n\n"+(url?"":"\n"));
            }

            AppRecord result = this.getAppRecord(appName, true);
            url = result.url
            return result
        }
    }

    public void stopApp(Map flags=[:]) {
        exec("vmc stop ${getAppName(flags)}");
    }
    
    public void destroyApp(Map flags=[:]) {
        useTarget(target, "delete ${getAppName(flags)}") {
            exec("vmc delete ${getAppName(flags)}");
        }
    }

    public void resizeAbsolute(Map flags=[:], int newSize) {
        if (newSize<0) throw new IllegalArgumentException("newSize cannot be negative for ${context}")
        useTarget(target, "resize ${getAppName(flags)} to ${newSize}") {
            exec("vmc instances ${getAppName(flags)} "+newSize);
        }
    }
    public void resizeDelta(Map flags=[:], int delta) {
        useTarget(target, "resize ${getAppName(flags)} ${(delta>=0?"+"+delta:delta)}") {
            exec("vmc instances ${getAppName(flags)} "+(delta>=0?"+"+delta:delta));
        }
    }

    public static class CloudFoundryAppStats {
        List<CloudFoundryAppStatLine> instances;
        CloudFoundryAppStatLine average;
        public int getSize() { instances.size() }
        @Override
        public String toString() {
            return "CloudFoundryAppStats[size="+size+";average="+average+"]";
        }
    }
    public static class CloudFoundryAppStatLine {
        double cpuUsage;
        int numCores;
        double memUsedMB;
        double memLimitMB;
        double memUsedFraction;
        
        double diskUsedMB;
        double diskLimitMB;
        double diskUsedFraction;
        
        long uptimeSeconds;
        
        @Override
        public String toString() {
            return "CloudFoundryStats["+
                "cpu="+cpuUsage+","+
                "cores="+numCores+","+
                "mem="+memUsedMB+"/"+memLimitMB+","+
                "disk="+diskUsedMB+"/"+diskLimitMB+","+
                "uptime="+uptimeSeconds+"]";
        }
        
        public static CloudFoundryAppStatLine parse(String l) {
            // | 0        | 0.0% (4)    | 116.6M (512M)  | 9.5M (2G)    | 0d:15h:41m:2s |
            String[] fields = l.split("\\s+");
            CloudFoundryAppStatLine result = new CloudFoundryAppStatLine();
            int i=3;
            result.cpuUsage = parseDouble(fields[i++]);
            result.numCores = parseInt(fields[i++]);
            i++
            result.memUsedMB = parseSizeMB(fields[i++]);
            result.memLimitMB = parseSizeMB(fields[i++]);
            result.memUsedFraction = result.memUsedMB/result.memLimitMB;
            i++
            result.diskUsedMB = parseSizeMB(fields[i++]);
            result.diskLimitMB = parseSizeMB(fields[i++]);
            result.diskUsedFraction = result.diskUsedMB/result.diskLimitMB;
            i++
            result.uptimeSeconds = parseTime(fields[i]);
            result
        }
        
        public static double parseDouble(String word) {
            if (word==null || word.length()==0) {
                log.warn("empty word found in stats, using 0")
                return 0;
            }
            if (word.startsWith("(")) word = word.substring(1);
            if (word.endsWith(")")) word = word.substring(0, word.length()-1);
            if (word.endsWith("%")) word = word.substring(0, word.length()-1);
            if (word.equals("NA")) return 0;  //returned early in lifecycle
            return Double.parseDouble(word);
        }
        public static int parseInt(String word) {
            if (word==null || word.length()==0) {
                log.warn("empty word found in stats, using 0")
                return 0;
            }
            if (word.startsWith("(")) word = word.substring(1);
            if (word.endsWith(")")) word = word.substring(0, word.length()-1);
            if (word.endsWith("%")) word = word.substring(0, word.length()-1);
            if (word.equals("NA")) return 0;  //returned early in lifecycle
            return Integer.parseInt(word);
        }        
        public static double parseSizeMB(String word) {
            if (word.startsWith("(")) word = word.substring(1, word.length()-1);
            if ("NA".equals(word)) return 0;
            double d = parseDouble(word.substring(0, word.length()-1));
            char c = word.charAt(word.length()-1)
            if (c=='M') return d;
            if (c=='G') return d*1024;
            if (c=='K') return d/1024.0;
            if (c=='B') return d/1024.0/1024.0;
            //perhaps one day:  :)
            if (c=='T') return d*1024*1024;
            if (c=='P') return d*1024*1024*1024;
            throw new IllegalArgumentException("Unparseable size $word");
        }
        public static long parseTime(String word) {
            if ("NA".equals(word)) return 0;
            int split = word.indexOf(':');
            String w = (split>=0 ? word.substring(0, split) : word);
            long t = parseInt(w.substring(0, w.length()-1));
            char c = w.charAt(w.length()-1);
            switch (c) {
                case 'd': t*=24;
                case 'h': t*=60;
                case 'm': t*=60;
                case 's': break;
                default: throw new IllegalArgumentException("Unparseable time $w"); 
            }
            if (split>=0) return t + parseTime(word.substring(split+1));
            else return t;
        }
        
        public static CloudFoundryAppStatLine average(Collection<CloudFoundryAppStatLine> stats) {
            CloudFoundryAppStatLine result = new CloudFoundryAppStatLine();
            for (CloudFoundryAppStatLine s: stats) {
                result.cpuUsage += s.cpuUsage;
                result.numCores += s.numCores;
                result.memUsedMB += s.memUsedMB;
                result.memLimitMB += s.memLimitMB;
                result.diskUsedMB += s.diskUsedMB;
                result.diskLimitMB += s.diskLimitMB;
                result.uptimeSeconds += s.uptimeSeconds;
            }
            int count = stats.size();
            if (count>0) {
                result.cpuUsage /= count;
                result.numCores /= count;
                result.memUsedMB /= count;
                result.memLimitMB /= count;
                result.diskUsedMB /= count;
                result.diskLimitMB /= count;
                result.uptimeSeconds /= count;
            }            
            result.memUsedFraction = result.memUsedMB/result.memLimitMB;
            result.diskUsedFraction = result.diskUsedMB/result.diskLimitMB;
            result
        }
    }
    
    public CloudFoundryAppStats stats(Map flags=[:]) {
        useTarget(target, "stats ${getAppName(flags)}") {
            String[] lines = exec("vmc stats ${getAppName(flags)}");
            //+----------+-------------+----------------+--------------+---------------+
            //| Instance | CPU (Cores) | Memory (limit) | Disk (limit) | Uptime        |
            //+----------+-------------+----------------+--------------+---------------+
            //| 0        | 0.0% (4)    | 116.6M (512M)  | 9.5M (2G)    | 0d:15h:41m:2s |
            //| 1        | 0.0% (4)    | 75.7M (512M)   | 9.4M (2G)    | 0d:9h:54m:44s |
            //+----------+-------------+----------------+--------------+---------------+
            List result = []

            def li = lines.iterator();
            li.next(); li.next(); li.next();  //skip 3 header lines
            while (li.hasNext()) {
                String line = li.next();
                if (line.startsWith("+---"))
                    continue;
                result << CloudFoundryAppStatLine.parse(line)
            }
            return new CloudFoundryAppStats(instances: result, average: CloudFoundryAppStatLine.average(result));
        }
    }
    
}
