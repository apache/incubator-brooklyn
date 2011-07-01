package brooklyn.location.basic

import brooklyn.location.PortRange

class BasicPortRange implements PortRange {

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
