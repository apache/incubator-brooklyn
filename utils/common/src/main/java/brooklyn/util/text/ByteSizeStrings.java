package brooklyn.util.text;

public class ByteSizeStrings {

    /** Formats byte sizes suitable for Java {@code -Xms} arguments. */
    public static final ByteSizeStrings java() {
        ByteSizeStrings strings = new ByteSizeStrings();
        strings.setBytesPerMetricUnit(1024);
        strings.setPrecision(0);
        strings.setMaxLen(6);
        strings.setSuffixBytes("");
        strings.setSuffixKilo("k");
        strings.setSuffixMega("m");
        strings.setSuffixGiga("g");
        return strings;
    }

    /** Formats byte sizes using ISO standard binary multiples of 1024. */
    public static ByteSizeStrings iso() {
        ByteSizeStrings strings = new ByteSizeStrings();
        strings.setBytesPerMetricUnit(1024);
        strings.setSuffixBytes("B");
        strings.setSuffixKilo("KiB");
        strings.setSuffixMega("MiB");
        strings.setSuffixGiga("GiB");
        return strings;
    }

    private String suffixBytes = "B";
    private String suffixKilo = "kB";
    private String suffixMega = "MB";
    private String suffixGiga = "GB";
    private int bytesPerMetricUnit = 1000;
    private int maxLen = 4;
    private int precision = 3;

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

        if (gb>2)
            return Strings.makeRealString(gb + (1.0*mb/bytesPerMetricUnit), maxLen, precision, 0) + suffixGiga;
        if (mb>2)
            return Strings.makeRealString((gb*bytesPerMetricUnit) + mb + (1.0*kb/bytesPerMetricUnit), maxLen, precision, 0) + suffixMega;
        if (kb>2)
            return Strings.makeRealString((mb*bytesPerMetricUnit) + kb + (1.0*b/bytesPerMetricUnit), maxLen, precision, 0) + suffixKilo;
        return (kb*bytesPerMetricUnit) + b + suffixBytes;
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
