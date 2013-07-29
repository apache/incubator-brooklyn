package brooklyn.util.net;


import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.math.BitList;
import brooklyn.util.math.BitUtils;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;

/** represents a CIDR (classless inter-domain routing) token, i.e. 10.0.0.0/8 or 192.168.4.0/24 */
public class Cidr {

    public static final Cidr UNIVERSAL = new Cidr();
    
    public static final Cidr _10 = new Cidr(10);
    public static final Cidr _172_16 = new Cidr("172.16.0.0/12");
    public static final Cidr _192_168 = new Cidr(192, 168);
    public static final Cidr CLASS_A = _10;
    public static final Cidr CLASS_B = _172_16;
    public static final Cidr CLASS_C = _192_168;
    
    public static final List<Cidr> PRIVATE_NETWORKS_RFC_1918 = ImmutableList.<Cidr>of(_192_168, _172_16, _10);
    
    public static final Cidr _169_254 = new Cidr("169.254.0.0/16");
    public static final Cidr LINK_LOCAL = _169_254;
    
    public static final Cidr _127 = new Cidr("127.0.0.0/8");
    public static final Cidr LOOPBACK = _127;

    public static final List<Cidr> NON_PUBLIC_CIDRS = ImmutableList.<Cidr>builder().addAll(PRIVATE_NETWORKS_RFC_1918).add(LINK_LOCAL).add(LOOPBACK).build();

    
    final int[] subnetBytes = new int[] { 0, 0, 0, 0 };
    final int length;
    
    
    public Cidr(String cidr) {
        if (Strings.isBlank(cidr))
            // useful e.g. if user leaves it blank in gui
            cidr = "0.0.0.0/0";
        int slash = cidr.indexOf('/');
        if (slash==-1) throw new IllegalArgumentException("CIDR should be of form 192.168.0.0/16 (missing slash)");
        String subnet = cidr.substring(0, slash);
        String lengthS = cidr.substring(slash+1);
        this.length = Integer.parseInt(lengthS);
        String[] bytes = subnet.split("\\.");
        int i=0;
        for (; i<this.length/8; i++)
            subnetBytes[i] = Integer.parseInt(bytes[i]);
        for (; i<(this.length+7)/8; i++)
            // for fractional parts: reverse significance, trim, reverse back
            subnetBytes[i] = 
                    BitUtils.unsigned( BitUtils.reverseBitSignificanceInByte(
                            BitList.newInstanceFromBytes(BitUtils.reverseBitSignificanceInByte(Integer.parseInt(bytes[i]))).
                            resized(this.length % 8).intValue() ));
    }
    
    /** returns true iff this CIDR is well-formed and canonical,
     * i.e. 4 dot-separated bytes followed by a slash and a length,
     * where length is <= 32, and the preceding 4 bytes don't include any 1 bits beyond the indicated length; 
     * e.g. 10.0.0.0/8 -- but not 10.0.0.1/8 or 10.../8
     * (although the latter ones are accepted by the constructor and converted to the canonical CIDR) */
    public static boolean isCanonical(String cidr) {
        try {
            return cidr.equals(new Cidr(cidr).toString());
        } catch (Throwable e) {
            Exceptions.propagateIfFatal(e);
            return false;
        }
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
        // reverse the bits to remove zeroed bits, then reverse back
        byte[] significantSubnetBytes = BitList.newInstance(BitUtils.reverseBitSignificanceInBytes(subnetBytes)).resized(length).asBytes();
        for (int i=0; i<significantSubnetBytes.length; i++)
            this.subnetBytes[i] = BitUtils.unsigned(BitUtils.reverseBitSignificance(significantSubnetBytes[i]));
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
        while (i<(length+7)/8) {
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
        return Networking.getInetAddressWithFixedName(netmaskBytes);
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
        return Networking.getInetAddressWithFixedName(bytes);
    }

    /** returns length of the prefix in common between the two cidrs */
    public int commonPrefixLength(Cidr other) {
        return asBitList().commonPrefixLength(other.asBitList());
    }

    public Cidr commonPrefix(Cidr other) {
        return new Cidr(other.getBytes(), commonPrefixLength(other));
    }

    /** returns list of bits for the significant (length) bits of this CIDR */ 
    public BitList asBitList() {
        return BitList.newInstance(BitUtils.reverseBitSignificanceInBytes(getBytes())).resized(getLength());
    }
    
    public boolean contains(Cidr target) {
        return commonPrefixLength(target) == getLength();
    }

    // FIXME remove from here, promote NetworkUtils
    /** @deprecated use {@link Networking#getInetAddressWithFixedName(byte[])} */
    @Deprecated
    public static InetAddress getInetAddressWithFixedName(byte[] ip) {
        return Networking.getInetAddressWithFixedName(ip);
    }

}
