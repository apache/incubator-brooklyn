package brooklyn.util.net;

public enum Protocol {

    TCP("tcp"),
    UDP("udp"),
    ICMP("icmp"),
    ALL("all");

    final String protocol;

    private Protocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public String toString() {
        return protocol;
    }
}
