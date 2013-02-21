package brooklyn.util.net;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import com.google.common.base.Throwables;

import brooklyn.util.NetworkUtils;

/** represents a CIDR (classless inter-domain routing) token, i.e. 10.0.0.0/8 or 192.168.4.0/24 */
public class Cidr {

    final int[] subnetBytes = new int[] { 0, 0, 0, 0 };
    final int length;
    
    
    public Cidr(String cidr) {
        int slash = cidr.indexOf('/');
        if (slash==-1) throw new IllegalArgumentException("CIDR should be of form 192.168.0.0/16 (missing slash)");
        String subnet = cidr.substring(0, slash);
        String lengthS = cidr.substring(slash+1);
        this.length = Integer.parseInt(lengthS);
        String[] bytes = subnet.split("\\.");
        for (int i=0; i<this.length/8; i++)
            subnetBytes[i] = Integer.parseInt(bytes[i]);
    }
    
    /** allows creation as  Cidr(192, 168)  for 192.168.0.0/16;
     * zero bits or ints included are significant, i.e. Cidr(10, 0) gives 10.0.0.0/16 */
    public Cidr(int ...unsignedBytes) {
        length = unsignedBytes.length*8;
        System.arraycopy(unsignedBytes, 0, subnetBytes, 0, unsignedBytes.length);
    }

    public Cidr(int[] subnetBytes, int length) {
        this.length = length;
        if (subnetBytes.length>4)
            throw new IllegalArgumentException("Cannot create CIDR beyond 4 bytes: "+Arrays.toString(subnetBytes));
        if (length>32)
            throw new IllegalArgumentException("Cannot create CIDR beyond 4 bytes: length "+length);
        System.arraycopy(subnetBytes, 0, this.subnetBytes, 0, Math.min(subnetBytes.length, (length+7)/8) );
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + length;
        result = prime * result + Arrays.hashCode(subnetBytes);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Cidr other = (Cidr) obj;
        if (length != other.length)
            return false;
        if (!Arrays.equals(subnetBytes, other.subnetBytes))
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int i=0;
        while (i<length/8) {
            if (sb.length()>0) sb.append(".");
            sb.append(""+subnetBytes[i]);
            i++;
        }
        while (i<4) {
            if (sb.length()>0) sb.append(".");
            sb.append("0");
            i++;
        }
        sb.append("/");
        sb.append(""+length);
        return sb.toString();
    }

    public int[] getBytes() {
        return Arrays.copyOf(subnetBytes, 4);
    }

    public int getLength() {
        return length;
    }

    public Cidr subnet(int ...extraUnsignedBytes) {
        if ((length%8)!=0) throw new IllegalStateException("subnet can only be used for byte boundary subnetted CIDR's; not "+this);
        int[] newBytes = getBytes();
        int newLen = this.length + extraUnsignedBytes.length*8;
        if (newLen>32) throw new IllegalStateException("further subnet for "+Arrays.toString(extraUnsignedBytes)+" not possible on CIDR "+this);
        for (int i=0; i<extraUnsignedBytes.length; i++) {
            newBytes[this.length/8 + i] = extraUnsignedBytes[i];
        }
        return new Cidr(newBytes, newLen);
    }

    /** returns the netmask for this cidr; e.g. for a /24 cidr returns 255.255.255.0 */
    public InetAddress netmask() {
        final byte[] netmaskBytes = new byte[] { 0, 0, 0, 0 };
        int lengthLeft = length;
        int i=0;
        while (lengthLeft>0) {
            if (lengthLeft>=8) {
                netmaskBytes[i] = (byte)255;
            } else {
                netmaskBytes[i] = (byte) ( (1 << lengthLeft) - 1 );
            }
            lengthLeft -= 8;
            i++;
        }
        try {
            return NetworkUtils.getInetAddressWithFixedName(netmaskBytes);
        } catch (UnknownHostException e) {
            // FIXME invalid length shouldn't be checked exception!
            throw Throwables.propagate(e);
        }
    }

    /** taking the addresses in the CIDR in order, returns the one in the offset^th position 
     * (starting with the CIDR itself, even if final bits are 0) */
    public InetAddress addressAtOffset(int offset) {
        int[] ints = getBytes();
        ints[3] += offset;
        {
            int i=3;
            while (ints[i]>=256) {
                ints[i-1] += (ints[i] / 256);
                ints[i] %= 256;
                i--;
            }
        }
        byte[] bytes = new byte[] { 0, 0, 0, 0 };
        for (int i=0; i<4; i++)
            bytes[i] = (byte) ints[i];
        try {
            return NetworkUtils.getInetAddressWithFixedName(bytes);
        } catch (UnknownHostException e) {
            // FIXME invalid length shouldn't be checked exception!
            throw Throwables.propagate(e);
        }
    }
}
