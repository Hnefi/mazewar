import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

public class ClientServerArbiter {

    //Map of client names to Client objects
    private final Map clientNameMap = new HashMap();

    public void requestLocalClientEvent(LocalClient c, ClientEvent ce){
        requestLocalClientEvent(c, ce, null);
    }

    public void requestLocalClientEvent(LocalClient c, ClientEvent ce, Client target){
        //Sends a request to the server regarding this client

        //Until the server is up, we'll hackily just mirror the request back
        String targetName = null;
        if (target != null) {
            targetName = target.getName();
        }
        processEvent(c.getName(), ce, targetName);
    }

    public void processEvent(String clientName, ClientEvent ce, String targetName){
        //Reacts to the response from the server regarding a client
        Object o = clientNameMap.get(clientName);
        assert(o instanceof Client);
        Client c = (Client)o;
               if (ce == ClientEvent.moveForward){
            c.forward();
        } else if (ce == ClientEvent.moveBackward){
            c.backup();
        } else if (ce == ClientEvent.turnLeft) {
           c.turnLeft(); 
        } else if (ce == ClientEvent.turnRight) {
            c.turnRight();
        } else if (ce == ClientEvent.invert) {
            c.invert();
        } else if (ce == ClientEvent.fire) {
            c.fire();
        }
    }

    public void addClient(Client c){
        clientNameMap.put(c.getName(), c);
        c.registerArbiter(this);
    }

    public void removeClient(Client c){
        clientNameMap.remove(c.getName());
        c.unregisterArbiter();
    }
}
