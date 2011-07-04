package brooklyn.location.basic

import brooklyn.location.PortRange

class BasicPortRange implements PortRange {

    public static final int MAX_PORT = 65535;
    public static final PortRange ANY_HIGH_PORT = new BasicPortRange(1024, MAX_PORT)
    
    private final int min;
    private final int max;
    
    public BasicPortRange(int min, int max) {
        this.min = min;
        this.max = max;
    }
    
    public int getMin() {
        return min;
    }
    
    public int getMax() {
        return max;
    }
}
