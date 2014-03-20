import java.util.*;

public class AddressPortPair {
    public InetAddress addr = null;
    public int port = -1;
    public AddressPortPair(InetAddress inAddr, int inPort){
        this.addr = inAddr;
        this.port = inPort;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        /* Now compare addresses and ports */
        if (this.addr.equals(obj.addr) 
            && this.port = obj.port) {
            return true;
            }
        return false;
    }
}


