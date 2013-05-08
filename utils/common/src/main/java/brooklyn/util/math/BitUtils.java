package brooklyn.util.math;

public class BitUtils {

    /** reverses the bits in a byte, i.e.  128 = 0b1000000 = bit list {0,0,0,0,0,0,0,1},
     * reversed yields 1 = 0b00000001 = bit list {1,0,0,0,0,0,0,0} */
    public static byte reverseBitSignificance(byte b) {
        int result = 0;
        for (int i=0; i<8; i++) {
            result <<= 1;
            if ((b&1)==1) result++;
            b >>= 1;
        }
        return (byte)result;
    }
    
    /** as {@link #reverseBitSignificance(byte)} but accepting int for convenience */
    public static byte reverseBitSignificanceInByte(int b) {
        return reverseBitSignificance((byte)b);
    }
    
    /** returns an array of bytes where the bits in each byte have been reversed;
     * note however the order of the arguments is not reversed;
     * useful e.g. in working with IP address CIDR's */
    public static byte[] reverseBitSignificance(byte ...bytes) {
        byte[] result = new byte[bytes.length];
        for (int i=0; i<bytes.length; i++)
            result[i] = reverseBitSignificance(bytes[i]);
        return result;
    }

    /** as {@link #reverseBitSignificance(byte...)}, but taking ints for convenience (ignoring high bits) */
    public static byte[] reverseBitSignificanceInBytes(int ...bytes) {
        byte[] result = new byte[bytes.length];
        for (int i=0; i<bytes.length; i++)
            result[i] = reverseBitSignificance((byte)bytes[i]);
        return result;
    }
    
    /** why oh why are bytes signed! */
    public static int unsigned(byte b) {
        if (b<0) return b+256;
        return b;
    }

    /** returns the value in 0..255 which is equivalent mod 256 */
    public static int unsignedByte(int b) {
        if (b<0) return (b%256)+256;
        return (b%256);
    }

}
