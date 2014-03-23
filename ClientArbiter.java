import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayDeque;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

/*START HELPER_CLASSES*/

class ClientQueueObject {
    public final ClientEvent eventType;
    public final String clientName;
    public final String targetName;
    public final DirectedPoint dPoint;
    public final Integer seed;

    public ClientQueueObject(ClientEvent eType, String cName, String tName, DirectedPoint p, Integer s){
        this.eventType = eType;
        this.clientName = cName;
        this.targetName = tName;
        this.dPoint = p;
        this.seed = s;
    }
}

class ClientBufferQueue {
    private final int INBUFFERSIZE = 1;
    private final ArrayBlockingQueue<ClientQueueObject> inBuf;
    private String clientName;

    public ClientBufferQueue(String cName){
        this.clientName = cName;
        this.inBuf = new ArrayBlockingQueue<ClientQueueObject>(INBUFFERSIZE);
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

    public ClientQueueObject takeFromBufNonBlocking(){
        //returns a null object if the buffer is empty instead of blocking until it's filled
        ClientQueueObject ret = null;
        if (!inBuf.isEmpty()){
            ret = takeFromBuf();
        }   
        return ret;
    }
}

class IncomingPacketObject {
    public Socket socket = null;
    public Token token = null;
    
    public IncomingPacketObject(Token t, Socket s){
        this.socket = s;
        this.token = t;
    }
}

class TokenHandlerQueue {
    private final int BUFFERSIZE = 3;
    private final ArrayBlockingQueue<IncomingPacketObject> buf;

    public TokenHandlerQueue(){
        this.buf = new ArrayBlockingQueue<IncomingPacketObject>(BUFFERSIZE); 
    }
    
    public void insertToBuf(IncomingPacketObject entry){
        try {
            buf.put(entry);
        } catch (InterruptedException x){
            Thread.currentThread().interrupt(); //propagate
        }
    }

    public IncomingPacketObject takeFromBuf(){
        IncomingPacketObject ret = null;
        try {
            ret = buf.take();
        } catch (InterruptedException x){
            Thread.currentThread().interrupt();
        }
        return ret;
    }
}

/*END HELPER CLASSES*/

/*START DUMMY THREADS*/

class PredecessorThread extends Thread {
    private final Socket predSocket;
    private final TokenHandlerQueue toHandlerBuf;

    public PredecessorThread(Socket s, TokenHandlerQueue b){
        super("PredecessorThread");
        predSocket = s;
        toHandlerBuf = b;
    }

    @Override
    public void run() {
        //Now establish Object streams to/from the server
        ObjectOutputStream toPred = null;
        ObjectInputStream fromPred = null;
        try {
            toPred = new ObjectOutputStream(predSocket.getOutputStream());
            fromPred = new ObjectInputStream(predSocket.getInputStream());
        } catch (IOException x) {
            System.err.println("PredecessorThread couldn't open input stream with message: " + x.getMessage());
        }
        System.out.println("PredecessorThread got new Input stream successfully.");

        //Main Loop
        while (!isInterrupted()){
            //Get a packet from the server
            Token tokenFromPred = null;
            try {
                tokenFromPred = (Token) fromPred.readObject();
            } catch (IOException x) {
                System.err.println("InBufferThread missed reading packet!!");
                continue;
            } catch (ClassNotFoundException cnf) {
                System.err.println("InBufferThread pulled out something that isn't a GamePacket.");
                continue;
            }
            assert(tokenFromPred != null);

            //Send the token to the TokenHandlerThread
            toHandlerBuf.insertToBuf(new IncomingPacketObject(tokenFromPred, null));
        }
        try{
            toPred.close();
            fromPred.close();
            predSocket.close();
        } catch (IOException x) {
            System.err.println("PredecessorThread couldn't close sockets " + x.getMessage());
        }
        System.out.println("PredecessorThread thread dying! Bye!");
    }
}

class ServerSocketThread extends Thread {
    private final ServerSocket server;
    private final TokenHandlerQueue toHandlerBuf;

    public ServerSocketThread(ServerSocket s, TokenHandlerQueue b){
        super("ServerSocketThread");
        server = s;
        toHandlerBuf = b;
    }

    @Override
    public void run() {
        //Main Loop
        while (!isInterrupted()){
            //Now establish Object streams to/from the server
            Socket sock = null;
            try {
                sock = server.accept();
            } catch (IOException x) {
                System.err.println("ServerSocketThread couldn't open input stream with message: " + x.getMessage());
            }
            System.out.println("ServerSocketThread got new Input stream successfully.");
            assert(sock != null);

            //2 Rules that we always impose for new connections to our server socket: 
            // [1] A new connection is established only from a machine which...
            //      During a join:  ...is trying to establish itself *as our successor*
            //      During a leave: ...is our successor and is leaving.
            //      During a replace: ...is replacing our old successor
            // [2] The first thing that remote machine will do is send a GamePacket representing whether it's joining, leaving, or replacing
            //      During a join:      Also includes its serverSocket port to send to our current successor
            //      During a leave:     No extra information included; but by virtue of leaving we will ACK the leave and not send the packet
            //                          to that successor anymore.
            //      During a replace:   No extra information is included; we can simply replace this socket with our successor
            //
            //We can't actually read the packet here though - since the TokenHandlerThread may need to keep communicating with this socket,
            //it has to be the one to open the Object streams!
            toHandlerBuf.insertToBuf(new IncomingPacketObject(null, sock));
        }
        try{
            server.close();
        } catch (IOException x) {
            System.err.println("ServerSocketThread couldn't close sockets " + x.getMessage());
        }
        System.out.println("ServerSocketThread thread dying! Bye!");
    }  

}

/*END DUMMY THREADS*/

class TokenHandlerThread extends Thread {
    //Since there are two sockets you're listening/blocking on, you need two dummy threads
    //which take packets from those sockets and put them into a buffer.
    private PredecessorThread predThread;
    private ServerSocketThread sockThread;
    private final TokenHandlerQueue fromSocketsBuf;
    
    //Buffers to send information to our clients
    private final ConcurrentHashMap<String, ClientBufferQueue> outBufMap;
    private final ConcurrentHashMap<String, ClientBufferQueue> inBufMap;

    //Sockets and Servers to connect to the ring
    private Socket predSocket;
    private AddressPortPair predPortPair;
    
    private Socket succSocket;
    private ObjectOutputStream streamToSuccessor;
    private ObjectInputStream streamFromSuccessor;

    private ServerSocket socketListener;

    //Arbiter handles ....something?
    private final ClientArbiter arbiter;

    boolean firstToConnect;

    public TokenHandlerThread(  ConcurrentHashMap<String, ClientBufferQueue> oBufMap,
                                ConcurrentHashMap<String, ClientBufferQueue> iBufMap, 
                                AddressPortPair predLoc,
                                int myServerPort,
                                boolean first,
                                ClientArbiter arb){
        super("TokenHandlerThread");
        this.outBufMap = oBufMap;
        this.inBufMap = iBufMap;
        this.arbiter = arb;
        this.firstToConnect = first;
        this.fromSocketsBuf = new TokenHandlerQueue();

        //Make a new socket based on predLoc;
        predPortPair = predLoc;
        try {
            predSocket = new Socket(predLoc.addr, predLoc.port);
            predThread = new PredecessorThread(predSocket, fromSocketsBuf);

            //Make a new server socket based on myServerPort;
            socketListener = new ServerSocket(myServerPort);
            sockThread = new ServerSocketThread(socketListener, fromSocketsBuf);

            //Successor starts off as my predecessor because I need to send it join protocol packets
            succSocket = new Socket(predLoc.addr, predLoc.port);
        } catch (IOException e){
            System.out.println("TokenHandlerThread failed to create with message: "+e.getMessage());
            System.exit(-1);
        }
    }

    @Override
    public void run() {
        //Start the dummy threads
        predThread.start();
        sockThread.start();

        //Now initiate and complete the join protocol to get you in the ring
        initiateJoinProtocol();

        //Now wait for something from the queue
        while(!isInterrupted()){
            IncomingPacketObject packet = fromSocketsBuf.takeFromBuf(); //blocks until there's something there

            if (packet.token != null){
                handleToken(packet.token);
            } else if (packet.socket != null){
                handleSocket(packet.socket);
            } else {
                System.out.println("OH NOES!");
            }
        }
        try{
            predThread.interrupt();
            sockThread.interrupt();
            succSocket.close();
        } catch (IOException x) {
            System.err.println("ServerSocketThread couldn't close sockets " + x.getMessage());
        }
        System.out.println("ServerSocketThread thread dying! Bye!");
    }

    private void handleToken(Token token){
        //if our predecessor is leaving, it tells us where to open the new connection to
        if (token.predecessorReplaceLoc != null){
            replacePredecessor(token.predecessorReplaceLoc);
            token.predecessorReplaceLoc = null;
        }
        
        //now render all of the events in the token
        ArrayDeque<GamePacket> localQ = new ArrayDeque<GamePacket>();
        GamePacket fromQ = null;
        boolean weAreLeaving = false;
        while ((fromQ = token.takeFromQ()) != null){
            GamePacket toQ = fromQ;

            ClientQueueObject tokenEvent = ClientArbiter.getClientQFromPacket(fromQ);

            if (tokenEvent.clientName == null){
                System.out.println("OH NOES! Null player name in the Token???? type = "+ClientArbiter.clientEventAsString(tokenEvent.eventType));
                continue;
            }
            ClientBufferQueue toClientQ = inBufMap.get(tokenEvent.clientName);
            if (toClientQ == null){ 
                System.out.println("Dropping packet talking about unknown client "+tokenEvent.clientName);
                continue;
            }
            toClientQ.insertToBuf(tokenEvent);

            if (arbiter.isLocalClientName(tokenEvent.clientName)){
                ClientBufferQueue fromClientQ = outBufMap.get(tokenEvent.clientName);
                ClientQueueObject clientEvent = fromClientQ.takeFromBufNonBlocking();
                if (clientEvent == null){
                    toQ = ClientArbiter.generateNopPacket(tokenEvent.clientName);
                } else {
                    if (clientEvent.eventType == ClientEvent.leave){
                        weAreLeaving = true;
                    }
                    toQ = ClientArbiter.getPacketFromClientQ(clientEvent);
                }
            }                  
            localQ.add(toQ);
        }
        
        //only ever initiated by a GUI client and starts everything shutting down!
        if (weAreLeaving){
            initiateLeaveProtocol(token);
        }

        //copy our version of the localQ back into the token
        token.overWriteEventQueue(localQ);

        //now pass the token on to our successor
        try {
            streamToSuccessor.writeObject(token);
        } catch (IOException x) {
            System.err.println("Sender couldn't write packet.");
        }
    }

    private void handleSocket(Socket socket){

    }
    
    private void initiateJoinProtocol(){ 
        //Join protocol
        //  [1] Open a connection to the machine who will be your predecessor and send then your server port
        //      [2] That machine will take the socket it creates for this connection and use it as its new successor
        //  [3] Wait for your successor to send you a socket on your server port
        //  [4] Open a connection to your successor, using updateSuccessor(true)
    }

    private void updatePredecessor(AddressPortPair newPred){
        //Kill the current PredecessorThread and replace it with a new one with an
        //open connection to the new predecessor location
    }

    private void updateSuccessor(AddressPortPair newSucc, boolean sendPredUpdate){
        //Replace the current successor socket with a new one at the new address
        //if sendPredUpdate is true, send a special Token to your current successor
        //containing newSucc.
        //Your old successor will either replace its predecesssor with newSucc (as in a join)
        //or recognize that it's ok to leave (as in a leave).
    }

    private void initiateLeaveProtocol(Token token){
        //send a message to your predecessor which will trigger a updateSuccessor(true) on
        //that machine 
    }

    private void replacePredecessor(AddressPortPair newPredLoc){
        //swap the current predecessor socket with a new one at this location; kill and restart the predThread
    }
    
    /*
    private void blockOnMessageFromSocket(Socket sock){
            ObjectOutputStream toNew = null;
            ObjectInputStream fromNew = null;
            try {
                toNew = new ObjectOutputStream(sock.getOutputStream());
                fromNew = new ObjectInputStream(sock.getInputStream());
            } catch (IOException x) {
                System.err.println("ServerSocketThread couldn't open input stream with message: " + x.getMessage());
            }
            System.out.println("ServerSocketThread got new Input stream successfully.");

            GamePacket newRingPacket = null;
            try {
                newRingPacket = (GamePacket) fromNew.readObject();
            } catch (IOException x) {
                System.err.println("ServerSocketThread missed reading packet!!");
                continue;
            } catch (ClassNotFoundException cnf) {ObjectOutputStream toNew = null;
            ObjectInputStream fromNew = null;
            try {
                toNew = new ObjectOutputStream(sock.getOutputStream());
                fromNew = new ObjectInputStream(sock.getInputStream());
            } catch (IOException x) {
                System.err.println("ServerSocketThread couldn't open input stream with message: " + x.getMessage());
            }
            System.out.println("ServerSocketThread got new Input stream successfully.");

            GamePacket newRingPacket = null;
            try {
                newRingPacket = (GamePacket) fromNew.readObject();
            } catch (IOException x) {
                System.err.println("ServerSocketThread missed reading packet!!");
                continue;
            } catch (ClassNotFoundException cnf) {
                System.err.println("ServerSocketThread pulled out something that isn't a GamePacket.");
                continue;
            }
            assert(newRingPacket != null);

            //Close the output streams so the TokenHandlerThread can open its own
            toNew.close();
            fromNew.close();     
                System.err.println("ServerSocketThread pulled out something that isn't a GamePacket.");
                continue;
            }
            assert(newRingPacket != null);

            //Close the output streams so the TokenHandlerThread can open its own
            toNew.close();
            fromNew.close();     
    }
    */
}

public class ClientArbiter {

    //Map of client names to Client objects
    private final ConcurrentHashMap<String, Client> clientNameMap;
    private final ConcurrentHashMap<Long, ClientEvent> threadWaitingOnMap;

    private final int OUTBUFFERSIZE = 50;

    private Socket toServerSocket;
    private ServerSocket fromServerListener;

    //Maps of queues for the RingThread to communicate back to the clients/arbiter
    private final ConcurrentHashMap<String, ClientBufferQueue> outBufferMap;
    private final ConcurrentHashMap<String, ClientBufferQueue> inBufferMap;

    //Queue specifically targetting the arbiter for actions, usually during the join protocol
    private final ClientBufferQueue arbiterBuffer;

    private final TokenHandlerThread tokenThread;

    private Maze maze;
    private final int seed;

    public ClientArbiter(String myClientName, AddressPortPair dnsLocation, int myPort, int seedArg){
        seed = seedArg;
        maze = null;
        
        clientNameMap = new ConcurrentHashMap<String, Client>();
        threadWaitingOnMap = new ConcurrentHashMap<Long, ClientEvent>();

        outBufferMap = new ConcurrentHashMap<String, ClientBufferQueue>();
        inBufferMap = new ConcurrentHashMap<String, ClientBufferQueue>();

        arbiterBuffer = new ClientBufferQueue("arbiter");
        inBufferMap.put("arbiter", arbiterBuffer);

        //#1: Contact the DNS to find everyone else's address/port
        //#2: Choose a player as your predecessor
        //#3: Construct a ringThread with that address/port, which will initiate a connection and
        //    start the join protocol.

        //Temp for compilation :)
        AddressPortPair predLocation = new AddressPortPair(null, -1);
        boolean firstToConnect = false;

        //Construct the RingThread, which will establish itself in the Ring
        tokenThread = new TokenHandlerThread(outBufferMap, inBufferMap, predLocation, myPort, firstToConnect, this);
        tokenThread.start();

        //Once the RingThread is inside the ring network, it will send us a message that it's OK to start a new Client
        //ClientQueueObject canJoin = arbiterBuffer.takeFromBuf();
        //assert(canJoin != null && canJoin.eventType == ClientEvent.clientProceed);
    }

    private static int getPacketTypeFromClientEvent(ClientEvent eType){
        int packetType = GamePacket.CLIENT_NULL;
        if (eType == ClientEvent.remoteLocation){
            packetType = GamePacket.CLIENT_REMOTE_LOC;
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
        } else if (eType == ClientEvent.nop){
            packetType = GamePacket.CLIENT_NOP;
        }
        return packetType;
    }
    private static ClientEvent getClientEventFromPacketType(int packetType){
        ClientEvent eType = null;
        switch(packetType){
            case GamePacket.CLIENT_REMOTE_LOC:
                eType = ClientEvent.remoteLocation;
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
            case GamePacket.SET_RAND_SEED:
                eType = ClientEvent.setRandomSeed;
                break;
            case GamePacket.CLIENT_NOP:
                eType = ClientEvent.nop;
                break;
            default:
                break;
        }
        return eType;
    }
    
    public static GamePacket generateNopPacket(String clientName){
        GamePacket packet = new GamePacket();
        packet.type = GamePacket.CLIENT_NOP;
        packet.player_name = clientName;
        return packet;
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
        return (new ClientQueueObject(eType, packet.player_name, packet.john_doe, dPoint, packet.seed));
    }

    public synchronized int getSeed(){
        return seed;
    }

    public boolean isLocalClientName(String clientName){
        Client thisClient = clientNameMap.get(clientName);
        return (thisClient != null && thisClient instanceof LocalClient);
    }

    public void handleRemoteLocationMessage(ClientQueueObject q){
        //Given a remote location message as part of the dynamic join protocol, create and spawn a corresponding remote client
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
        //This means opening a connection to your successor (referred to by the DNS)
        //and sending them your name so they can intiate a join cycle with the existing
        //participants of the ring and everyone can allocate a new client for you
        requestLocalClientEvent(c, ClientEvent.join); 

        //TODO: Block until the token is received from your successor
        //Token t = ringThread.blockUntilTokenReceived()
        
        //Now go through the Token and...
        //  - for each client, spawn a RemoteClient at the location in the queue
        //  - open a connection to your successor's predecessor (insert yourself in the ring)
        //  - clear the "Player is Joining" bit
        //  - choose a spawn location and put a spawn event into the queue

        /*
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
                //Allocate and spawn a new client
                handleRemoteLocationMessage(objectFromServer);
            } else {
                System.out.println("ERROR: Received unexpected packet of type " + clientEventAsString(eventFromServer) + " during add protocol.");
            }
        }
        */
        //Finally, spawn this client randomly in the maze!
        maze.randomSpawnClient(c);
    }

    public void createRemoteClientAndSendLocations(String remoteClientName){
        //Invoked on a pre-exisiting client machine on another machine trying to join
        System.out.println("Arbiter: Creating a new remote client for client " + remoteClientName + " and replying with locations.");
        //First create the client
        maze.createRemoteClient(remoteClientName);

        //Note that we *will not* spawn the new client here; that will wait until we get
        //notified where it should spawn.

        //Now iterate through all the LocalClients you know about and send their locations
        //to the server
        for (Client c : clientNameMap.values()){
            if (c instanceof LocalClient){
                //sendClientLocationToServer((LocalClient)c);
            }
        }
    }

    public boolean waitForEventAndProcess(String clientName){ 
        ClientBufferQueue myInBuffer = inBufferMap.get(clientName);
        if(myInBuffer != null){
            processEvent(myInBuffer.takeFromBuf());
            return true;
        }
        return false;
    }

    public boolean requestServerAction(Client c){
        //This method is called in a tight loop for remote clients
        //Basically just block until the server instructs you to do something, do that something, and continue
        String clientName = null;
        if (c != null){
            clientName = c.getName();
        }
        assert(clientName != null);

        return waitForEventAndProcess(clientName);
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

    public static String clientEventAsString(ClientEvent ce){
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
        } else if (ce == ClientEvent.leave) {
            ret = "LEAVE";
        } else if (ce == ClientEvent.remoteLocation) {
            ret = "REMOTE_LOCATION";
        } else if (ce == ClientEvent.setRandomSeed) {
            ret = "SET_RANDOM_SEED";
        } else if (ce == ClientEvent.leave) {
            ret = "LEAVE";
        } else if (ce == ClientEvent.nop){
            ret = "NOP";
        } else {
            ret = "UNKNOWN";
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

        //Write the request to the output buffer; let the output thread care about the Lclock setting
        ClientBufferQueue outBuffer = outBufferMap.get(clientName);
        outBuffer.insertToBuf(new ClientQueueObject(ce, clientName, targetName, p, null));

        //Record that this thread is currently waiting for a reply
        Long curThreadId = Thread.currentThread().getId();
        assert(threadWaitingOnMap.get(curThreadId) == null);

        if(ce == ClientEvent.leave){
            //if we request to leave, we expect to be told to die.
            //ce = ClientEvent.die;
        }

        threadWaitingOnMap.put(curThreadId, ce);

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
        Client c = clientNameMap.get(clientName);
        assert(c != null);
        
        ClientEvent waitingOn = threadWaitingOnMap.get(Thread.currentThread().getId());
        if (c instanceof LocalClient && waitingOn != ce){
            String waitingString = clientEventAsString(waitingOn);
            assert (waitingString != null);
            String processString = clientEventAsString(ce);
            assert (processString != null);
            System.out.println("ERROR: Thread ID #" + Thread.currentThread().getId() + " waiting on event " + waitingString + " but got event " + processString);
            assert(waitingOn == ce);
        }
        threadWaitingOnMap.remove(Thread.currentThread().getId());
        

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
        } else if (ce == ClientEvent.leave) {
            c.leave();
        }/*
        } else if (ce == ClientEvent.die){
            outBuffer.insertToBuf(new ClientQueueObject(ce, null, null, null, null, 0));
        }*/
    }

    public void addClient(Client c){
        System.out.println("Arbiter: Adding client with name " + c.getName());
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
