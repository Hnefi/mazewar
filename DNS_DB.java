import java.net.*;
import java.io.*;
import java.util.*;


/* Simple backend database that stores a list of all connected clients. Supports
 * add and remove methods (it's obvious why). 
 */
public class DNS_DB {
    private ArrayList<AddressPortPair> registry = null;

    public DNS_DB(){
        this.registry = new ArrayList<AddressPortPair>();
   }

    public ArrayList<AddressPortPair> get_all_address_except_for(String excludeAddr, int excludePort){
        /*used for forwarding; given a registered IP address & port, return all sockets except 
        that one in the registry*/
        ArrayList<AddressPortPair> other_sockets = new ArrayList<AddressPortPair>();

        boolean excluded_self = false;
        Enumeration<String> enumKey = registry.keys();
        while (enumKey.hasMoreElements()){
            String name = enumKey.nextElement();
            AddressPortPair broker = registry.get(name);

            if (broker.addr.equals(excludeAddr)
                && broker.port == excludePort){
                excluded_self = true;
            } else {
                other_sockets.add(broker);
            }
        }
        if (!excluded_self){
            System.out.println("IP address "+excludeAddr+" requested other IPs without registering!");
            return null;
        }
        return other_sockets;
    }

    public void register_name_and_dest(InetAddress addr, int port){
        this.registry.add(new AddressPortPair(addr, port));
    }

    public boolean remove_exchange(AddressPortPair to_remove){
        return this.registry.remove(to_remove);
        // overrode AddressPortPair equality so this is supported
    }

    public AddressPortPair get_socket(String name){
        AddressPortPair ret = null;
        if (this.registry.containsKey(name)){
            ret = this.registry.get(name);
        }
        return ret;
    }
}
