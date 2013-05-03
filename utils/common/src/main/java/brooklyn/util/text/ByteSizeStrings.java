package brooklyn.util.text;

public class ByteSizeStrings {

    String suffixBytes = "b";
    String suffixKilo = "kb";
    String suffixMega = "mb";
    String suffixGiga = "gb";
    int bytesPerMetricUnit = 1000;
    int maxLen = 4;
    int precision = 3;
    
    public String makeSizeString(long size) {
        long t = size;
        if (t==0) return "0"+suffixBytes;
        if (t<0) return "-"+makeSizeString(-t);
        long b = t%bytesPerMetricUnit;
        t = t/bytesPerMetricUnit;
        long kb = t%bytesPerMetricUnit;
        t = t/bytesPerMetricUnit;
        long mb = t%bytesPerMetricUnit;
        t = t/bytesPerMetricUnit;
        long gb = t;
        
        if (gb>0)
            return Strings.makeRealString(gb + (1.0*mb/bytesPerMetricUnit), maxLen, precision, 0) + suffixGiga;
        if (mb>0)
            return Strings.makeRealString(mb + (1.0*kb/bytesPerMetricUnit), maxLen, precision, 0) + suffixMega;
        if (kb>0)
            return Strings.makeRealString(kb + (1.0*b/bytesPerMetricUnit), maxLen, precision, 0) + suffixKilo;
        return b + suffixBytes;
    }

    public void setBytesPerMetricUnit(int bytesPerMetricUnit) {
        this.bytesPerMetricUnit = bytesPerMetricUnit;
    }
    public void setMaxLen(int maxLen) {
        this.maxLen = maxLen;
    }
    public void setPrecision(int precision) {
        this.precision = precision;
    }
    public void setSuffixBytes(String suffixBytes) {
        this.suffixBytes = suffixBytes;
    }
    public void setSuffixGiga(String suffixGiga) {
        this.suffixGiga = suffixGiga;
    }
    public void setSuffixKilo(String suffixKilo) {
        this.suffixKilo = suffixKilo;
    }
    public void setSuffixMega(String suffixMega) {
        this.suffixMega = suffixMega;
    }
    
}
