package brooklyn.entity.proxy.nginx;

import java.io.Serializable;

/** records a rewrite rule for use in URL rewriting such as by nginx;
 * from and to are expected to be usual regex replacement strings,
 * with the convention here (for portability) that:
 * <li>
 * <it> from should match the entire path (internally is wrapped with ^ and $ for nginx);
 * <it> to can refer to $1, $2 from the groups in from
 * </li>
 * so eg use from = (.*)A(.*)  and to = $1B$2 to change all occurrences of A to B
 */
public class UrlRewriteRule implements Serializable {
    
    private static final long serialVersionUID = -8457441487467968553L;
    
    String from, to;
    boolean isBreak;
    
    /* there is also a flag "last" possible on nginx which might be useful,
     * but i don't know how portable that is --
     * we'll know e.g. when we support HA Proxy and others.
     * presumably everything has at least one "break-after-this-rewrite" mode
     * so i think we're safe having one in here.
     */

    public UrlRewriteRule() {}
    public UrlRewriteRule(String from, String to) {
        this.from = from;
        this.to = to;
    }
    
    public String getFrom() {
        return from;
    }
    public void setFrom(String from) {
        this.from = from;
    }
    public String getTo() {
        return to;
    }
    public void setTo(String to) {
        this.to = to;
    }
        
    public boolean isBreak() {
        return isBreak;
    }
    public void setBreak(boolean isBreak) {
        this.isBreak = isBreak;
    }

    public UrlRewriteRule setBreak() { setBreak(true); return this; }
    
}
