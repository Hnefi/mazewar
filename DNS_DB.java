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

    public ArrayList<AddressPortPair> get_clients_except_for(AddressPortPair me) {
        // return new instance of clients (except this one)
        ArrayList<AddressPortPair>ret_list = new ArrayList<AddressPortPair>(this.registry);
        ret_list.remove(me);
        return ret_list;
    }

    public void register_name_and_dest(AddressPortPair newguy) {
        this.registry.add(newguy);
    }
    
    /* This takes an InetAddress since it's not specific to
     * the socket/port pair (since that is a client listening
     * port used for new connections).
     */
    public boolean remove_client(InetAddress to_remove){
        // get iterator and remove from the arrayList
        boolean removed = false;
        Iterator<AddressPortPair> it = registry.iterator();
        while (it.hasNext()) {
            if (it.next().addr.equals(to_remove)) {
                it.remove();
                removed = true;
                break; // since this inetAddr is unique.
            }
        }
        return removed;
    }
}
