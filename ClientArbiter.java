import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

class ClientQueueObject {
    public final ClientEvent eventType;
    public final String clientName;
    public final String targetName;
    public final DirectedPoint dPoint;

    public ClientQueueObject(ClientEvent eType, String cName, String tName, DirectedPoint p){
        this.eventType = eType;
        this.clientName = cName;
        this.targetName = tName;
        this.dPoint = p;
    }
}

class ClientInBuffer {
    private final int INBUFFERSIZE = 1;
    private final ArrayBlockingQueue<ClientQueueObject> inBuf;
    private String clientName;

    public ClientInBuffer(String cName){
        this.clientName = cName;
        this.inBuf = new ArrayBlockingQueue<ClientQueueObject>(1);
    }
   
    public void insertToBuf(ClientQueueObject entry){
        try {
            inBuf.put(entry);
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt(); // propagate
        }
    }
    
    public ClientQueueObject takeFromBuf(){
        ClientQueueObject ret = null;   
        try {
            ret = inBuf.take(); // blocking
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt(); // propagate
        }
        return ret; 
    }
}


//TODO: Split this class into two, each using a socket to communicate with the server
class BufferThread extends Thread {
    private final ArrayBlockingQueue<ClientQueueObject> outBuf;
    private final ConcurrentHashMap<String, ClientInBuffer> inBufMap;

    public BufferThread(ArrayBlockingQueue<ClientQueueObject> oBuf, ConcurrentHashMap<String, ClientInBuffer> iBufs){
        super("BufferThread");
        this.outBuf = oBuf;
        this.inBufMap = iBufs;
        this.start();
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()){
                ClientQueueObject messageToServer = this.outBuf.take();
                assert(messageToServer != null);

                String clientName = messageToServer.clientName;
                ClientInBuffer bufferToClient = this.inBufMap.get(clientName);
                assert(bufferToClient != null);

                bufferToClient.insertToBuf(messageToServer);
            }
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt(); //propagate
        }
    }
}

public class ClientArbiter {

    //Map of client names to Client objects
    private final Map clientNameMap = new HashMap();

    private final int OUTBUFFERSIZE = 50;

    private final ArrayBlockingQueue<ClientQueueObject> outBuffer;
    private final ConcurrentHashMap<String, ClientInBuffer> inBufferMap;
    private final BufferThread bThread;
   
    public ClientArbiter(){
        outBuffer = new ArrayBlockingQueue<ClientQueueObject>(OUTBUFFERSIZE);
        inBufferMap = new ConcurrentHashMap<String, ClientInBuffer>();
        bThread = new BufferThread(outBuffer, inBufferMap);
    }

    public int getSeed(){
        return 42;
    }

    public void waitForEventAndProcess(String clientName){ 
        ClientInBuffer myInBuffer = inBufferMap.get(clientName);
        assert(myInBuffer != null);
        processEvent(myInBuffer.takeFromBuf());
    }

    public void requestLocalClientEvent(LocalClient c, ClientEvent ce){
        requestLocalClientEvent(c, ce, null, null);
    }

    public void requestLocalClientEvent(LocalClient c, ClientEvent ce, Client target){
        requestLocalClientEvent(c, ce, target, null);
    }

    public void requestLocalClientEvent(LocalClient c, ClientEvent ce, DirectedPoint p){
        requestLocalClientEvent(c, ce, null, p);
    }

    public void requestLocalClientEvent(LocalClient c, ClientEvent ce, Client target, DirectedPoint p)    {
        //Sends a request to the server regarding this client
        String clientName = null;
        if(c != null){
            clientName = c.getName();
        }

        String targetName = null;
        if (target != null) {
            targetName = target.getName();
        }

        //Write the request to the output buffer
        try {
            outBuffer.put(new ClientQueueObject(ce, clientName, targetName, p));
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt(); // propagate
        }

        //Now wait until your input buffer gets populated and process the event
        waitForEventAndProcess(clientName);
    }

    public void processEvent(ClientQueueObject fromQ){
        assert(fromQ != null);
        String clientName = fromQ.clientName;
        ClientEvent ce = fromQ.eventType;
        String targetName = fromQ.targetName;
        DirectedPoint p = fromQ.dPoint;

        //Reacts to the response from the server regarding a client
        Object o = clientNameMap.get(clientName);
        assert(o instanceof Client);
        Client c = (Client)o;
        assert(c != null);
        if        (ce == ClientEvent.moveForward){
            if (c instanceof GUIClient){
                System.out.println("Client " + clientName + " moved forward via the arbiter!");
            }
            c.forward();
        } else if (ce == ClientEvent.moveBackward){
            if (c instanceof GUIClient){
                System.out.println("Client " + clientName + " backed up via the arbiter!");
            }
            c.backup();
        } else if (ce == ClientEvent.turnLeft) {
            if (c instanceof GUIClient){
                System.out.println("Client " + clientName + " turned left via the arbiter!");
            }
            c.turnLeft(); 
        } else if (ce == ClientEvent.turnRight) {
            if (c instanceof GUIClient){
                System.out.println("Client " + clientName + " turned right via the arbiter!");
            }
            c.turnRight();
        } else if (ce == ClientEvent.invert) {
            if (c instanceof GUIClient){
                System.out.println("Client " + clientName + " inverted via the arbiter!");
            }
            c.invert();
        } else if (ce == ClientEvent.fire) {
            if (c instanceof GUIClient){
                System.out.println("Client " + clientName + " fired via the arbiter!");
            }
            c.fire();
        } else if (ce == ClientEvent.spawn) {
            if (c instanceof GUIClient){
                System.out.println("Client " + clientName + " spawned via the arbiter!");
            }
            c.spawn(p);
        } else if (ce == ClientEvent.kill) {
            assert(targetName != null);
            Object t = clientNameMap.get(targetName);
            assert(t instanceof Client);
            if (c instanceof GUIClient || t instanceof GUIClient){
                System.out.println("Client " + clientName + " killed client " + targetName + " via the arbiter!");
            }
            Client target = (Client)t;
            assert(target != null);
            c.kill(target);
        }
    }

    public void addClient(Client c){
        clientNameMap.put(c.getName(), c);
        inBufferMap.put(c.getName(), new ClientInBuffer(c.getName()));
        c.registerArbiter(this);
    }

    public void removeClient(Client c){
        clientNameMap.remove(c.getName());
        inBufferMap.remove(c.getName());
        c.unregisterArbiter();
    }
}
