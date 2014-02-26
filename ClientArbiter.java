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

class ClientBufferQueue {
    private final int INBUFFERSIZE = 1;
    private final ArrayBlockingQueue<ClientQueueObject> inBuf;
    private String clientName;

    public ClientBufferQueue(String cName){
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
class OutBufferThread extends Thread {
    private final ClientBufferQueue outBuf;

    //TODO: Replace this with an open socket to the server
    private final ClientBufferQueue socket;

    public OutBufferThread(ClientBufferQueue oBuf, ClientBufferQueue socketProxy){
        super("OutBufferThread");
        this.outBuf = oBuf;
        this.socket = socketProxy;
        this.start();
    }

    @Override
    public void run() {
        while (true){
            ClientQueueObject messageToServer = this.outBuf.takeFromBuf();
            assert(messageToServer != null);

            //TODO: Format the ClientQueueObject as a GamePacket for the server
            //TODO: Replace this with a socket put
            this.socket.insertToBuf(messageToServer);

            //Until we actually connect to the server, fake it to look like we've received all other locations.
            if (messageToServer.eventType == ClientEvent.join){
                this.socket.insertToBuf(new ClientQueueObject(ClientEvent.locationComplete, messageToServer.clientName, null, null));
            }
        }
    }
}

class InBufferThread extends Thread {
    private final ClientBufferQueue socket;
    private final ConcurrentHashMap<String, ClientBufferQueue> inBufMap;
    private final ClientArbiter arbiter;

    public InBufferThread(ClientBufferQueue socketProxy, ConcurrentHashMap<String, ClientBufferQueue> iBufs, ClientArbiter arb){
        super("InBufferThread");
        this.socket = socketProxy;
        this.inBufMap = iBufs;
        this.arbiter = arb;
        this.start();
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()){
            ClientQueueObject messageFromServer = this.socket.takeFromBuf();
            assert(messageFromServer != null);

            String clientName = messageFromServer.clientName;

            //Find the right queue to put the message into
            ClientBufferQueue bufferToClient = this.inBufMap.get(clientName);
            
            if(messageFromServer.eventType == ClientEvent.join && bufferToClient == null){
                System.out.println("InBufferThread creating new RemoteClient names " + clientName);
                
                //We've never seen this client before - must be a new remote client!
                arbiter.createRemoteClientAndSendLocations(clientName);

            } else {
                assert(bufferToClient != null);

                //now forward the packet to the appropriate client!
                bufferToClient.insertToBuf(messageFromServer);
            }
        }
    }
}

public class ClientArbiter {

    //Map of client names to Client objects
    private final ConcurrentHashMap<String, Client> clientNameMap;
    private final ConcurrentHashMap<Long, ClientEvent> threadWaitingOnMap;

    private final int OUTBUFFERSIZE = 50;

    private final ClientBufferQueue socketProxy;
    private final ClientBufferQueue outBuffer;
    private final ConcurrentHashMap<String, ClientBufferQueue> inBufferMap;
    private final OutBufferThread outThread;
    private final InBufferThread inThread;

    private Maze maze;
   
    public ClientArbiter(){
        clientNameMap = new ConcurrentHashMap<String, Client>();
        threadWaitingOnMap = new ConcurrentHashMap<Long, ClientEvent>();

        outBuffer = new ClientBufferQueue("masterOutBuffer");
        inBufferMap = new ConcurrentHashMap<String, ClientBufferQueue>();

        //TODO: Open socket protocol; open two sockets to the server (send & receive)
        socketProxy = new ClientBufferQueue("socketProxy");

        //TODO: Pass the socket to the buffer threads
        outThread = new OutBufferThread(outBuffer, socketProxy);
        inThread = new InBufferThread(socketProxy, inBufferMap, this);

        maze = null;
    }

    private static int getPacketTypeFromClientEvent(ClientEvent eType){
        int packetType = GamePacket.CLIENT_NULL;
        if(eType == ClientEvent.locationRequest){
            packetType = GamePacket.LOCATION_REQ;
        } else if (eType == ClientEvent.locationResponse){
            packetType = GamePacket.LOCATION_RESP;
        } else if (eType == ClientEvent.remoteLocation){
            packetType = GamePacket.REMOTE_LOC;
        } else if (eType == ClientEvent.locationComplete){
            packetType = GamePacket.ALL_LOC_DONE;
        } else if (eType == ClientEvent.moveForward){
            packetType = GamePacket.CLIENT_MOVED_FORWARD;
        } else if (eType == ClientEvent.moveBackward){
            packetType = GamePacket.CLIENT_MOVED_BACK;
        } else if (eType == ClientEvent.invert){
            packetType = GamePacket.CLIENT_INVERT;
        } else if (eType == ClientEvent.turnLeft){
            packetType = GamePacket.CLIENT_TURN_L;
        } else if (eType == ClientEvent.turnRight){
            packetType = GamePacket.CLIENT_TURN_R;
        } else if (eType == ClientEvent.fire){
            packetType = GamePacket.CLIENT_FIRED;
        } else if (eType == ClientEvent.kill){
            packetType = GamePacket.CLIENT_KILLED;
        } else if (eType == ClientEvent.spawn){
            packetType = GamePacket.CLIENT_SPAWNED;
        } else if (eType == ClientEvent.join){
            packetType = GamePacket.CLIENT_JOINED;
        } else if (eType == ClientEvent.leave){
            packetType = GamePacket.CLIENT_LEFT;
        }
        return packetType;
    }
    private static ClientEvent getClientEventFromPacketType(int packetType){
        ClientEvent eType = null;
        switch(packetType){
            case GamePacket.LOCATION_REQ:
                eType = ClientEvent.locationRequest;
                break;
            case GamePacket.LOCATION_RESP:
                eType = ClientEvent.locationResponse;
                break;
            case GamePacket.REMOTE_LOC:
                eType = ClientEvent.remoteLocation;
                break;
            case GamePacket.ALL_LOC_DONE:
                eType = ClientEvent.locationComplete;
                break;
            case GamePacket.CLIENT_MOVED_FORWARD:
                eType = ClientEvent.moveForward;
                break;
            case GamePacket.CLIENT_MOVED_BACK:
                eType = ClientEvent.moveBackward;
                break;
            case GamePacket.CLIENT_INVERT:
                eType = ClientEvent.invert;
                break;
            case GamePacket.CLIENT_TURN_L:
                eType = ClientEvent.turnLeft;
                break;
            case GamePacket.CLIENT_TURN_R:
                eType = ClientEvent.turnRight;
                break;
            case GamePacket.CLIENT_FIRED:
                eType = ClientEvent.fire;
                break;
            case GamePacket.CLIENT_KILLED:
                eType = ClientEvent.kill;
                break;
            case GamePacket.CLIENT_SPAWNED:
                eType = ClientEvent.spawn;
                break;
            case GamePacket.CLIENT_JOINED:
                eType = ClientEvent.join;
                break;
            case GamePacket.CLIENT_LEFT:
                eType = ClientEvent.leave;
                break;
            default:
                break;
        }
        return eType;
    }

    public static GamePacket getPacketFromClientQ(ClientQueueObject qObject){
        assert(qObject != null);

        GamePacket packet = new GamePacket();
        packet.type = getPacketTypeFromClientEvent(qObject.eventType);
        packet.player_name = qObject.clientName;
        packet.location = qObject.dPoint;
        packet.john_doe = qObject.targetName;
        return packet;
    }

    public static ClientQueueObject getClientQFromPacket(GamePacket packet){
        assert(packet != null);

        //First find out what type of event we're dealing with
        ClientEvent eType = getClientEventFromPacketType(packet.type);
        DirectedPoint dPoint = packet.location;
        if (dPoint == null){
            dPoint = new DirectedPoint(packet.you_are_here, packet.i_want_it_that_way);
        }

        return (new ClientQueueObject(eType, packet.player_name, packet.john_doe, dPoint));
    }

    public int getSeed(){
        return 42;
    }

    public void handleRemoteLocationMessage(ClientQueueObject q){
        assert(q != null);
        assert(q.eventType == ClientEvent.remoteLocation);
        assert(maze != null);

        String remoteName = q.targetName;
        DirectedPoint remotePoint = q.dPoint;

        Client existingClient = clientNameMap.get(remoteName);
        if (existingClient == null){
            RemoteClient rClient = maze.createRemoteClient(remoteName);
            maze.spawnClient(rClient, remotePoint); 
        }
    }

    public void addLocalClientAndLoadRemoteClients(LocalClient c){
        //Invoked on a new client machine trying to join an exiting game
        assert(c != null);
        assert(c.getName() != null);
        assert(maze != null);

        //Add the LocalClient in the maze and the arbiter!
        maze.addClient(c);

        //Send a join packet and block until it comes back
        requestLocalClientEvent(c, ClientEvent.join); 

        //Now get this client's input buffer
        ClientBufferQueue myInBuffer = inBufferMap.get(c.getName());
        //Now wait for the locations of every other player, spawning a new RemoteClient each time
        ClientQueueObject objectFromServer = null;
        ClientEvent eventFromServer = null;
        while(true){
            objectFromServer = myInBuffer.takeFromBuf();
            assert(objectFromServer != null);
            eventFromServer = objectFromServer.eventType;
            if (eventFromServer == ClientEvent.locationComplete){
                break;
            } else if (eventFromServer == ClientEvent.remoteLocation){
                handleRemoteLocationMessage(objectFromServer);
            } else {
                System.out.println("ERROR: Received unexpected packet of type " + clientEventAsString(eventFromServer) + " during add protocol.");
            }
        }

        //Finally, spawn the client in the maze!
        maze.randomSpawnClient(c);
    }

    public void sendClientLocationToServer(LocalClient c){
        String clientName = null;
        if (c != null){
            clientName = c.getName();
        }
        assert(clientName != null);

        DirectedPoint dPoint = null;
        if (c != null){
            Point p = c.getPoint();
            Direction d = c.getOrientation();
            dPoint = new DirectedPoint(p, d);
        }
        assert(dPoint != null);

        //write it to the output buffer for the outThread to find
        outBuffer.insertToBuf(new ClientQueueObject(ClientEvent.locationRequest, clientName, null, dPoint));
    }

    public void createRemoteClientAndSendLocations(String remoteClientName){
        //Invoked on a pre-exisiting client machine on another machine trying to join

        //First create the client
        maze.createRemoteClient(remoteClientName);

        //Note that we *will not* spawn the new client here; that will wait until we get
        //notified where it should spawn.

        //Now iterate through all the LocalClients you know about and send their locations
        //to the server
        for (Client c : clientNameMap.values()){
            if (c instanceof LocalClient){
                sendClientLocationToServer((LocalClient)c);
            }
        }
    }

    public void waitForEventAndProcess(String clientName){ 
        ClientBufferQueue myInBuffer = inBufferMap.get(clientName);
        assert(myInBuffer != null);
        processEvent(myInBuffer.takeFromBuf());
    }

    public void requestServerAction(Client c){
        //This method is called in a tight loop for remote clients
        //Basically just block until the server instructs you to do something, do that something, and continue
        String clientName = null;
        if (c != null){
            clientName = c.getName();
        }
        assert(clientName != null);

        waitForEventAndProcess(clientName);
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

    public String clientEventAsString(ClientEvent ce){
        String ret = null;
        if        (ce == ClientEvent.moveForward){
            ret = "FORWARD";
        } else if (ce == ClientEvent.moveBackward){
            ret = "BACKWARD";
        } else if (ce == ClientEvent.turnLeft) {
            ret = "LEFT";
        } else if (ce == ClientEvent.turnRight) {
            ret = "RIGHT";
        } else if (ce == ClientEvent.invert) {
            ret = "INVERT";
        } else if (ce == ClientEvent.fire) {
            ret = "FIRE";
        } else if (ce == ClientEvent.spawn) {
            ret = "SPAWN";
        } else if (ce == ClientEvent.kill) {
            ret = "KILL";
        } else if (ce == ClientEvent.join) {
            ret = "JOIN";
        } else if (ce == ClientEvent.locationRequest) {
            ret = "LOCATION_REQUEST";
        }
        return ret;
    }

    public void requestLocalClientEvent(LocalClient c, ClientEvent ce, Client target, DirectedPoint p)    {
        //Sends a request to the server regarding this client
        String clientName = null;
        if(c != null){
            clientName = c.getName();
        }
        assert(clientName != null);

        String targetName = null;
        if (target != null) {
            targetName = target.getName();
        }
        if (ce == ClientEvent.kill){
            assert(targetName != null);
        }

        //Write the request to the output buffer
        outBuffer.insertToBuf(new ClientQueueObject(ce, clientName, targetName, p));

        //Record that this thread is currently waiting for a reply
        Long curThreadId = Thread.currentThread().getId();
        assert(threadWaitingOnMap.get(curThreadId) == null);

        threadWaitingOnMap.put(Thread.currentThread().getId(), ce);

        //Now wait until your input buffer gets populated and process the event
        waitForEventAndProcess(clientName);
    }

    public void processEvent(ClientQueueObject fromQ){
        assert(fromQ != null);

        String clientName = fromQ.clientName;
        ClientEvent ce = fromQ.eventType;
        String targetName = fromQ.targetName;
        DirectedPoint p = fromQ.dPoint;

        ClientEvent waitingOn = threadWaitingOnMap.get(Thread.currentThread().getId());
        if (waitingOn != ce){
            String waitingString = clientEventAsString(waitingOn);
            assert (waitingString != null);
            String processString = clientEventAsString(ce);
            assert (processString != null);
            System.out.println("ERROR: Thread ID #" + Thread.currentThread().getId() + " waiting on event " + waitingString + " but got event " + processString);
            assert(waitingOn == ce);
        }
        threadWaitingOnMap.remove(Thread.currentThread().getId());
        
        //Reacts to the response from the server regarding a client
        Client c = clientNameMap.get(clientName);
        assert(c != null);

        if        (ce == ClientEvent.moveForward){
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
        } else if (ce == ClientEvent.spawn) {
            c.spawn(p);
        } else if (ce == ClientEvent.kill) {
            assert(targetName != null);
            
            Client target = clientNameMap.get(targetName);
            assert(target != null);

            c.kill(target);
        }
    }

    public void addClient(Client c){
        clientNameMap.put(c.getName(), c);
        inBufferMap.put(c.getName(), new ClientBufferQueue(c.getName()));
        c.registerArbiter(this);
    }

    public void removeClient(Client c){
        clientNameMap.remove(c.getName());
        inBufferMap.remove(c.getName());
        c.unregisterArbiter();
    }

    public void registerMaze(Maze m){
        assert(m != null);
        this.maze = m;
        maze.addArbiter(this);
    }
}
