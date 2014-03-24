import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Random;
import java.net.*;

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
    public final Integer score;

    public ClientQueueObject(ClientEvent eType, String cName, String tName, DirectedPoint p, Integer s, Integer sc){
        this.eventType = eType;
        this.clientName = cName;
        this.targetName = tName;
        this.dPoint = p;
        this.seed = s;
        this.score = sc;
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
    private final GamePacket first_pack;

    public PredecessorThread(Socket s, TokenHandlerQueue b,GamePacket p){
        super("PredecessorThread");
        predSocket = s;
        toHandlerBuf = b;
        first_pack = p;
    }

    @Override
    public void run() {
        //Now establish Object streams to/from the server
        ObjectOutputStream toPred = null;
        ObjectInputStream fromPred = null;
        try {
            System.out.println("New predThread attempting to open i/o streams.");
            toPred = new ObjectOutputStream(predSocket.getOutputStream());
            toPred.flush();
            fromPred = new ObjectInputStream(predSocket.getInputStream());
            System.out.println("Opened streams successfully.");
        } catch (IOException x) {
            System.err.println("PredecessorThread couldn't open input stream with message: " + x.getMessage());
        }
        System.out.println("PredecessorThread got new Input stream successfully.");

        try {// write first "setup" packet
            toPred.writeObject(first_pack);
        } catch (IOException x) {
            System.err.println("PredThread couldn't send RING_* first packet." + x.getMessage());
        }

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
                System.out.println("Server socket just returned a new connection from: " + sock.getInetAddress().toString() + " on port : " + sock.getPort());
            } catch (IOException x) {
                System.err.println("ServerSocketThread couldn't accept new connect with message: " + x.getMessage());
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
            System.out.println("Server socket put a new IncomingPacketObject (obviously a new connection socket) in the token handler queue!");
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
    private final int myServerPort;
    private final TokenHandlerQueue fromSocketsBuf;

    //Buffers to send information to our clients
    private final ConcurrentHashMap<String, ClientBufferQueue> outBufMap;
    private final ConcurrentHashMap<String, ClientBufferQueue> inBufMap;

    //Sockets and Servers to connect to the ring
    private Socket predSocket;
    private AddressPortPair predPortPair;
    private AddressPortPair temp_port_pair; // used for holding new joiners
    private Socket succSocket;
    private ObjectOutputStream streamToSuccessor;
    private ObjectInputStream streamFromSuccessor;

    // "next successor" variables (to make join protocol easier)
    private Socket next_successor_sock = null;
    private ObjectOutputStream next_succ_out_stream = null;
    private ObjectInputStream next_succ_in_stream = null;

    private ServerSocket socketListener;

    //Arbiter handles join/drop protocols and sending locations to joining machines
    private final ClientArbiter arbiter;

    boolean firstToConnect;
    boolean successorStreamReady = false;
    boolean cleanup_join_remnants = false;
    boolean send_locations = false;

    public TokenHandlerThread(  ConcurrentHashMap<String, ClientBufferQueue> oBufMap,
            ConcurrentHashMap<String, ClientBufferQueue> iBufMap, 
            AddressPortPair predLoc,
            int servPort,
            boolean first,
            ClientArbiter arb){
        super("TokenHandlerThread");
        this.outBufMap = oBufMap;
        this.inBufMap = iBufMap;
        this.arbiter = arb;
        this.myServerPort = servPort;
        this.firstToConnect = first;
        this.fromSocketsBuf = new TokenHandlerQueue();

        //Make a new socket based on predLoc
        try{
            System.out.println("New token handler thread being made with InetAddr: " + InetAddress.getLocalHost() + " and local listen port: " + servPort);
        } catch (UnknownHostException x) {
            System.err.println("Coudln't get my own local host.... for some reason....");
        }
        if (firstToConnect) {
            System.out.println(" ------ This guy is the first guy to connect!! ------ ");
        }
        System.out.println("Location we are going to join on is: " + predLoc.addr.toString() + ":" + predLoc.port);

        predPortPair = predLoc;
        try {
            socketListener = new ServerSocket(myServerPort);
            sockThread = new ServerSocketThread(socketListener, fromSocketsBuf);
            sockThread.start();

        } catch (IOException e){
            System.out.println("TokenHandlerThread failed to create with message: "+e.getMessage());
            System.exit(-1);
        }
    }

    @Override
    public void run() {
        // dummy threads now made/started by join protocol

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
            System.err.println("Error upon TokenHandlerThread killing threads and dying: " + x.getMessage());
        }
        System.out.println("TokenHandlerThread dying!! gg yo");
    }

    private void handleToken(Token token){
        //System.out.println("Thread ID #"+Thread.currentThread().getId()+" processing a token.");
        //if our predecessor is leaving, it tells us where to open the new connection to
        if (token.predecessorReplaceLoc != null){
            updatePredecessor(token.predecessorReplaceLoc);
            token.predecessorReplaceLoc = null;
        }

        //now render all of the events in the token
        ArrayDeque<GamePacket> localQ = new ArrayDeque<GamePacket>();
        
        //If we've just added a new machine, we need to send the locations of all our clients around to those machines
        //before any events get processed
        if (send_locations){
            arbiter.addAllClientLocations(localQ);
            send_locations = false;
        }
        if (cleanup_join_remnants && !firstToConnect) {
            send_locations = true;
            token.predecessorReplaceLoc = temp_port_pair;
            temp_port_pair = null;
        }

        //Now start extracting events from the Token and handling them
        GamePacket fromQ = null;
        boolean pushAllClientsLeaving = false; //performed when we detect that a client wants to leave
        boolean weAreLeaving = false; //performed when we pull leave packets out of the token for a LocalClient
        while ((fromQ = token.takeFromQ()) != null){
            GamePacket toQ = fromQ;

            ClientQueueObject tokenEvent = ClientArbiter.getClientQFromPacket(fromQ);

            if (tokenEvent.clientName == null){
                System.out.println("OH NOES! Null player name in the Token???? type = "+ClientArbiter.clientEventAsString(tokenEvent.eventType));
                continue;
            }

            if (tokenEvent.eventType == ClientEvent.leave && arbiter.isLocalClientName(tokenEvent.clientName)){
                weAreLeaving = true;
            }

            boolean shouldSendPacketToClient = (tokenEvent.eventType != ClientEvent.nop);
            if (shouldSendPacketToClient){
                System.out.println("Handling token event of type "+ClientArbiter.clientEventAsString(tokenEvent.eventType)+" for client "+tokenEvent.clientName);
            }

            if ((tokenEvent.eventType == ClientEvent.join || tokenEvent.eventType == ClientEvent.remoteLocation) && !arbiter.isLocalClientName(tokenEvent.clientName)){
                System.out.println("Creating a remote client called "+tokenEvent.clientName);
                arbiter.createRemoteClient(tokenEvent);
            } else {
                shouldSendPacketToClient &= (tokenEvent.eventType != ClientEvent.remoteLocation);
            }

            ClientBufferQueue toClientQ = inBufMap.get(tokenEvent.clientName);
            if (toClientQ == null){
                if (tokenEvent.eventType != ClientEvent.nop){ 
                    System.out.println("Dropping packet of type "+ClientArbiter.clientEventAsString(tokenEvent.eventType)+" talking about unknown client "+tokenEvent.clientName);
                }
                shouldSendPacketToClient = false;
            }

            if (shouldSendPacketToClient){
                System.out.println("TokenHandlerThread sending "+ClientArbiter.clientEventAsString(tokenEvent.eventType)+" event to client "+tokenEvent.clientName);
                toClientQ.insertToBuf(tokenEvent);
            }

            if (arbiter.isLocalClientName(tokenEvent.clientName)){
                ClientBufferQueue fromClientQ = outBufMap.get(tokenEvent.clientName);
                ClientQueueObject clientEvent = fromClientQ.takeFromBufNonBlocking();
                if (clientEvent != null){
                    System.out.println("Received an event "+ClientArbiter.clientEventAsString(clientEvent.eventType)+" from client "+clientEvent.clientName);
                    toQ = ClientArbiter.getPacketFromClientQ(clientEvent);
                    if (clientEvent.eventType == ClientEvent.leave && !weAreLeaving){
                        pushAllClientsLeaving = true;
                    }
                } else {
                    toQ = ClientArbiter.generateNopPacket(tokenEvent.clientName);
                }
            }                  
            localQ.add(toQ);
        }
        if (token.getEventQSize() != 0){
            System.out.println("OH NOES! I left "+token.getEventQSize()+"Events unprocessed for some reason!!!");
            System.exit(-1);
        }

        //Now pull from the special arbiter buffer looking for join packets
        while ((fromQ = arbiter.getPacketFromArbQ()) != null){
            //just put these packets straight into the token; in Arbiter we trust!
            localQ.add(fromQ);
        }

        //only ever initiated by a GUI client and starts everything shutting down!
        if (weAreLeaving){
            initiateLeaveProtocol(token); //this thread dies in here (not necessarily)
        } else if (pushAllClientsLeaving){
            arbiter.pushAllLocalClientsLeaving(localQ);
        }

        //copy our version of the localQ back into the token
        token.overWriteEventQueue(localQ);

        //now pass the token on to our successor
        try {
            //System.out.println("Done everything, trying to write token out the successor stream...");
            while (successorStreamReady == false) {
                // block until we get one
                synchronized (streamToSuccessor) {
                    try { 
                        streamToSuccessor.wait();   
                    } catch (InterruptedException x) {
                        interrupt();
                    }
                }
            }
            streamToSuccessor.writeObject(token);
        } catch (IOException x) {
            System.err.println("Sender couldn't write packet.");
        } 
        //System.out.println("Token passed.");

        if (cleanup_join_remnants && !firstToConnect) {
            // update our successor objects to write to. (clear the tmp variables as well)
            succSocket = next_successor_sock;
            streamToSuccessor = next_succ_out_stream;
            streamFromSuccessor = next_succ_in_stream;

            next_succ_out_stream = null;
            next_succ_in_stream = null;
            next_successor_sock = null;

            cleanup_join_remnants = false;
        }
        firstToConnect = false;
        if (weAreLeaving){
            arbiter.signalDie();
            interrupt();
        }
    }

    private void handleSocket(Socket socket){
        // this gets called whenever the serverSocket passes us a new socket object, need to block on it and wait to 
        // determine what kind of connection this was. this socket belongs to us, so we can do this w/o worrying about
        // stream sharing between threads (which is bad)
        System.out.println("Got into handleSocket()....");
        ObjectInputStream from_socket = null;
        ObjectOutputStream to_socket = null;
        try {
            System.out.println("Opening new streams in handleSocket()");
            to_socket = new ObjectOutputStream(socket.getOutputStream());
            to_socket.flush();
            from_socket = new ObjectInputStream(socket.getInputStream()); 
            System.out.println("Streams opened successfully.");
        } catch (IOException x) {
            System.err.println("IOException creating streams in handleSocket() fcn: " + x.getMessage());
        }

        GamePacket first_pack = null;
        try {
            first_pack = (GamePacket) from_socket.readObject(); // blocking
        } catch (IOException x) {
            System.err.println("IOException reading first object in handleSocket() fcn: " + x.getMessage());
        } catch (ClassNotFoundException x) {
            System.err.println("ClassNotFoundException reading first object in handleSocket(): " + x.getMessage());
        }

        // process the first packet we got back
        if (first_pack.type == GamePacket.RING_JOIN) {
            cleanup_join_remnants = true; // for next time we get token
            //send_locations = true;
            // This new connection is going to be our successor.
            temp_port_pair = new AddressPortPair(socket.getInetAddress(),first_pack.port);

            System.out.println("Received socket with first packet RING_JOIN, our new successor will be: " + socket.getInetAddress().toString() + " : " + first_pack.port);


            if (firstToConnect) {
                succSocket = socket;
                streamToSuccessor = to_socket;
                streamFromSuccessor = from_socket;
                successorStreamReady = true;
                
                System.out.println("We are first to connect, so replacing this successor socket immediately..... Creating blank token and putting it in my queue manually.");
                /* Now make a new token and put it in our local queue. */
                Token new_t = new Token();
                fromSocketsBuf.insertToBuf(new IncomingPacketObject(new_t,null));
                send_locations = false;
                cleanup_join_remnants = false;
                return;
            }

            // update successor to be this new socket (NEXT TIME)
            this.next_successor_sock = socket;
            this.next_succ_out_stream = to_socket;
            this.next_succ_in_stream = from_socket;
           
        } else if (first_pack.type == GamePacket.RING_REPLACE) {
            /* This is triggered on both join/leave, and it just tells us to replace our successor with THIS thing that
             * just connected to us.
             */

            System.out.println("Received socket with first packet RING_REPLACE, our new successor will be: " + socket.getInetAddress().toString());

            try {
                if (streamToSuccessor != null) {
                    streamToSuccessor.close();
                }
                if (streamFromSuccessor != null) {
                    streamFromSuccessor.close();
                }
                if (succSocket != null) {
                    succSocket.close();
                }
                System.out.println("Successfully closed old successor sockets....");
            } catch (IOException x) {
                System.err.println("IOException closing old successor networking primitives on receiving RING_REPLACE. " + x.getMessage());
            }
            succSocket = socket;
            streamToSuccessor = to_socket;
            streamFromSuccessor = from_socket;
            successorStreamReady = true;

            synchronized (streamToSuccessor) {
                streamToSuccessor.notifyAll(); // wakes any sleeping threads here.
            }

            System.out.println("Replaced all successor sockets and streams, my work here is done......");
        } else if (first_pack.type == GamePacket.RING_INVALIDATE) {
            /* Need to assign all our successor variables to NULL (will cause any tokens getting here to stall)
             * until we get a ring_replace to re-construct them.
             */
            System.out.println("Got a ring invalidate, setting all of my successor variables to NULL.");
            succSocket = null;
            streamToSuccessor = null;
            streamFromSuccessor = null;
            successorStreamReady = false;
            
        } else if (first_pack.type == GamePacket.RING_NOP) {
            // update successor to be this new socket (NEXT TIME)
            this.next_successor_sock = socket;
            this.next_succ_out_stream = to_socket;
            this.next_succ_in_stream = from_socket;

        } else {
            System.err.println("Wrong first packet upon opening a new client connection?!?!");
            System.exit(-1);
        }
    }

    private void initiateJoinProtocol(){ 
        //Join protocol (after checking we are not the only one in the ring)
        //  [1] Open a connection to the machine who will be your predecessor and send it a ring_join w. your server port
        //      [2] That machine will take the socket it creates for this connection and use it as it's new successor.
        //      [2a] You can now create your predecessor thread and sleep it on that socket. It just gets tokens.
        //  [3] Wait for your successor to send you a socket on your server port.
        //  [4] You now have your successor's socket, so open streams and use it as your successor from now on.
        //  [5] The first time you get the token, we can render all the locations (this is handled elsewhere).
     
        GamePacket join_pack = new GamePacket();
        join_pack.type = GamePacket.RING_JOIN;
        join_pack.port = myServerPort;
        
        /* Now we can create a predecessor thread for this join point (it will send a ring_nop as first operation) */
        try { 
            predSocket = new Socket(predPortPair.addr,predPortPair.port);
        } catch (IOException x) {
            System.err.println("IOException in creating predSocket (in join protocol): " + x.getMessage());
        }
        System.out.println("Join protocol is making a new predThread which will send a RING_JOIN back to " +predPortPair.addr.toString() + " : " + predPortPair.port + ". Server port of new client is " + join_pack.port);
        predThread = new PredecessorThread(predSocket,fromSocketsBuf,join_pack);
        predThread.start();

        /* Our predecessor is now set..... This is the end of our "join protocol initiate", but more is to be done when
         * our successor sends us a new socket.
         */
    }

    private void updatePredecessor(AddressPortPair newPred){
        //Kill the current PredecessorThread and replace it with a new one with an
        //open connection to the new predecessor location
        predPortPair = newPred;
        predThread.interrupt();
        try {
            System.out.println("Killed old predThread... Now making new socket to the following address: " + newPred.addr.toString() + " : " + newPred.port);
            predSocket = new Socket(newPred.addr,newPred.port);
        } catch (IOException x) {
            System.err.println("Error creating new socket in updatePredecessor. " + x.getMessage());
        }
        GamePacket first_pack = new GamePacket();
        first_pack.type = GamePacket.RING_REPLACE;
        predThread = new PredecessorThread(predSocket,fromSocketsBuf,first_pack);
        predThread.start();
        System.out.println("Made new thread object, it will send RING_REPLACE to the above socket address (already printed).");
    }

    private void initiateLeaveProtocol(Token token){
        //leave protocol:
        //      first need to tell OUR predecessor to invalidate its successor (will remove racing the token around the ring)
        //  [1] We HAVE the token right now - basically all we need to do is send along with the token the location of our
        //      predecessor. It will then replace ITS predecessor thread.
        //  [2] that new predThread will send a RING_REPLACE packet through the channel, which will cause the old predecessor
        //      to update its successor socket immediately. 
        //      IMPORTANT: How to deal with the race condition between this RING_REPLACE packet and the actual token?! (fixed)
        //  [3] Contact the dns and tell it that we are leaving the game.
 
        /* Talk to dns and unregister */
        AddressPortPair dnsLocation = arbiter.getDnsLocation();
        Socket dns_sock = null;
        ObjectOutputStream to_dns = null;
        ObjectInputStream from_dns = null;
        try {
            dns_sock = new Socket(dnsLocation.addr,dnsLocation.port);
            to_dns = new ObjectOutputStream(dns_sock.getOutputStream());
            to_dns.flush();
            from_dns = new ObjectInputStream(dns_sock.getInputStream());
            System.out.println("MADE new dns in/out streams in initiateLeaveProtocol()");
        } catch (IOException x) {
            System.err.println("Error opening connection to player lookup server: " + x.getMessage());
        }

        GamePacket location_lookup = new GamePacket();
        location_lookup.type = GamePacket.RING_LEAVE;

        GamePacket packet_w_status = null;
        try {
            to_dns.writeObject(location_lookup); // send and wait for lookup reply

            packet_w_status = (GamePacket) from_dns.readObject(); // blocking

            System.out.println("Got gamepacket back from dns with type: " + packet_w_status.type);

            location_lookup.type = GamePacket.RING_NOP;
            to_dns.writeObject(location_lookup); // gracefully close dns connection
            System.out.println("Finsihed gracefully closing dns connection");
        }catch (IOException x) {
            System.err.println("Error sending leave request and reading response from player lookup server: " + x.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("ClassNotFoundException when reading response from lookup server: " + e.getMessage());
        }

        /* Now handle the case where we are the LAST one to leave the ring. interrupt myself and die */
        if (packet_w_status.type == GamePacket.RING_LAST_PLAYER) {
            arbiter.signalDie();
            interrupt();
            return;
        }
     
        System.out.println("Proceed to fixing up connections, we are NOT the last player.");
        Socket sock = null;
        ObjectOutputStream newOut = null;
        ObjectInputStream newIn = null;
        try {
            sock = new Socket(predPortPair.addr,predPortPair.port); // new connection to predecessor
            newOut = new ObjectOutputStream(sock.getOutputStream());
            newOut.flush();
            newIn = new ObjectInputStream(sock.getInputStream());
        } catch (IOException x) {
            System.err.println("tokenhandlerThread got error on trying to open new connection to pred to invalidate: " + x.getMessage());
        }

        GamePacket first = new GamePacket();
        first.type = GamePacket.RING_INVALIDATE;

        try { 
            newOut.writeObject(first);
        }  catch (IOException x) {
            System.err.println("tokenhandlerThread got error on trying to send first packet on leave protocol: " + x.getMessage());
        }

        token.predecessorReplaceLoc = predPortPair; // saved already
    }
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
    private final ClientBufferQueue dieBuffer;

    private final TokenHandlerThread tokenThread;

    private Maze maze;
    private final int seed;
    
    private final AddressPortPair dns_pair;

    public ClientArbiter(String myClientName, AddressPortPair dnsLocation, int myPort){
        maze = null;
        dns_pair = dnsLocation;
        clientNameMap = new ConcurrentHashMap<String, Client>();
        threadWaitingOnMap = new ConcurrentHashMap<Long, ClientEvent>();

        outBufferMap = new ConcurrentHashMap<String, ClientBufferQueue>();
        inBufferMap = new ConcurrentHashMap<String, ClientBufferQueue>();
        arbiterBuffer = new ClientBufferQueue(null);
        dieBuffer = new ClientBufferQueue(null);

        boolean firstToConnect = false;
        //#1: Contact the DNS to find everyone else's address/port
        //#2: Choose a player as your predecessor
        //#3: Construct a ringThread with that address/port, which will initiate a connection and
        //    start the join protocol.

        // Connect to the DNS and get the list of all of the other locations.
        Socket dns_sock = null;
        ObjectOutputStream to_dns = null;
        ObjectInputStream from_dns = null;
        try {
            dns_sock = new Socket(dnsLocation.addr,dnsLocation.port);
            to_dns = new ObjectOutputStream(dns_sock.getOutputStream());
            from_dns = new ObjectInputStream(dns_sock.getInputStream());
        } catch (IOException x) {
            System.err.println("Error opening connection to player lookup server: " + x.getMessage());
        }

        GamePacket location_lookup = new GamePacket();
        location_lookup.type = GamePacket.RING_JOIN;
        location_lookup.port = myPort; // we listen on this port - and lookupserver maintains <InetAddr,serverPort> pairs

        GamePacket packet_w_locations = null;
        try {
            to_dns.writeObject(location_lookup); // send and wait for lookup reply

            packet_w_locations = (GamePacket) from_dns.readObject(); // blocking
        }catch (IOException x) {
            System.err.println("Error sending lookup request and reading response from player lookup server: " + x.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("ClassNotFoundException when reading response from lookup server: " + e.getMessage());
        }
        seed = packet_w_locations.seed;

        /* Parse these locations and pick one as a ring "entry point" */
        AddressPortPair predLocation = null;
        ArrayList<AddressPortPair> other_players = packet_w_locations.list_of_others;
        if(other_players == null || other_players.isEmpty()) { // we are the first
            firstToConnect = true;
            try {
                predLocation = new AddressPortPair(InetAddress.getLocalHost(), myPort);
            } catch (java.net.UnknownHostException e) {
                System.out.println("Couldn't get local host name with message "+e.getMessage());
                System.exit(-1);
            }
        } else { // pick a client to "join on"
            predLocation = other_players.get(0);
        }

        
        try {
            GamePacket close = new GamePacket();
            close.type = GamePacket.RING_NOP;
            to_dns.writeObject(close);
        } catch (IOException x) {
            System.err.println("Error closing sockets to player lookup server: " + x.getMessage());
        } 

        //Construct the TokenHandlerThread, which will establish itself in the Ring
        System.out.println("Thread ID #"+Thread.currentThread().getId()+" creating the TokenHandlerThread.");
        tokenThread = new TokenHandlerThread(outBufferMap, inBufferMap, predLocation, myPort, firstToConnect, this);
        tokenThread.start();
    }

    public AddressPortPair getDnsLocation() {
        return dns_pair;
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
        packet.score = (qObject.score == null) ? -1 : qObject.score.intValue();
        return packet;
    }

    public static ClientQueueObject getClientQFromPacket(GamePacket packet){
        assert(packet != null);

        //First find out what type of event we're dealing with
        ClientEvent eType = getClientEventFromPacketType(packet.type);
        DirectedPoint dPoint = packet.location;
        return (new ClientQueueObject(eType, packet.player_name, packet.john_doe, dPoint, packet.seed, packet.score));
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

    public synchronized int getSeed(){
        return seed;
    }

    public boolean isLocalClientName(String clientName){
        Client thisClient = clientNameMap.get(clientName);
        return (thisClient != null && thisClient instanceof LocalClient);
    }

    public GamePacket getPacketFromArbQ(){
        //Reads a packet from the arbiter queue; only used during joins as a central point the TokenHandlerThread can always use
        //instead of iterating through the buffer map to find a new client.
        GamePacket retPacket = null;    
        ClientQueueObject fromArb = arbiterBuffer.takeFromBufNonBlocking();
        if (fromArb != null && fromArb.clientName != null){
            retPacket = getPacketFromClientQ(fromArb);
        }
        return retPacket;
    }

    public void signalDie(){
        System.out.println("Thread ID #"+Thread.currentThread().getId()+" signalling arbiter to die");
        dieBuffer.insertToBuf(new ClientQueueObject(null, null, null, null, null, null));
    }

    public void waitUntilDieSignal(){
        System.out.println("Thread ID #"+Thread.currentThread().getId()+" waiting for arbiter die signal");
        ClientQueueObject diePacket = null;
        while (diePacket == null){
            diePacket = dieBuffer.takeFromBuf();
        }
        System.out.println("Thread ID #"+Thread.currentThread().getId()+" received arbiter die signal");
    }

    public void createRemoteClient(ClientQueueObject q){
        //Given a remote location message as part of the dynamic join protocol, create and spawn a corresponding remote client
        assert(q != null);
        assert(q.eventType == ClientEvent.remoteLocation);
        assert(maze != null);

        String remoteName = q.clientName;
        DirectedPoint remotePoint = q.dPoint;
        int score = q.score;
        if (score == -1){
            //Newly joining client!
            score = 0;
        }
        System.out.println("Creating a remote Client for client "+remoteName);
        Client existingClient = clientNameMap.get(remoteName);
        if (existingClient == null){
            RemoteClient rClient = maze.createRemoteClient(remoteName);
            if (remotePoint != null){
                maze.spawnClient(rClient, remotePoint);
            }
            maze.setClientScore(rClient, score);
        }
    }

    public void addLocalClient(LocalClient c){
        //Invoked on a new client machine trying to join an exiting game
        assert(c != null);
        assert(c.getName() != null);
        assert(maze != null);

        //Add the LocalClient in the maze and the arbiter!
        maze.addClient(c);

        //Send a join action around the ring
        //If it's the first time (aka you're joining the ring as well) the first time you
        //get the packet, you will render all the remote guys at their locations and put the
        //join request into the Token. This call will return once the token comes back again,
        //which means we're guaranteed to know where everyone is.
        requestLocalClientEvent(c, ClientEvent.join); 

        //Now that we've joined, spawn in to the maze!
        maze.randomSpawnClient(c);
    }

    public boolean waitForEventAndProcess(String clientName){ 
        ClientBufferQueue myInBuffer = inBufferMap.get(clientName);
        if(myInBuffer != null){
            ClientQueueObject clientEvent = null;
            while (clientEvent == null){
                clientEvent = myInBuffer.takeFromBuf();
            }
            processEvent(clientEvent);
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

        //Write the request to the Client's output buffer
        ClientBufferQueue outBuffer = outBufferMap.get(clientName);
        if (ce == ClientEvent.join){
            //Put joins in the arbiter buffer so the TokenHandlerThread can easily find them
            outBuffer = arbiterBuffer;
        }
        outBuffer.insertToBuf(new ClientQueueObject(ce, clientName, targetName, p, null, null));

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
        if (c instanceof LocalClient && waitingOn != ce && ce != ClientEvent.leave){
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
        } else if (ce == ClientEvent.join) {
            //do nothing if you receive your join packet back
        } else {
            System.out.println("WARNING: Thread ID #" + Thread.currentThread().getId() + " processed unhandled event " + clientEventAsString(ce));
        }
    }

    public void pushAllLocalClientsLeaving(ArrayDeque<GamePacket> queue){
        System.out.println("Setting all my LocalClients to leaving!");
        for (Client c : clientNameMap.values()){
            if (!(c instanceof LocalClient)){
                continue;
            }
            GamePacket leavePacket = new GamePacket();
            leavePacket.type = GamePacket.CLIENT_LEFT;
            leavePacket.player_name = c.getName();
            queue.add(leavePacket); 
        }
    }

    public void addAllClientLocations(ArrayDeque<GamePacket> queue){
        System.out.println("Adding locations of all my clients!");
        for (Client c : clientNameMap.values()){
            GamePacket locPacket = new GamePacket();
            locPacket.type = GamePacket.CLIENT_REMOTE_LOC;
            locPacket.player_name = c.getName();
            locPacket.location = new DirectedPoint(c.getPoint(), c.getOrientation());
            locPacket.score = maze.getClientScore(c);
            queue.add(locPacket); 
        }
    }

    public void addClient(Client c){
        System.out.println("Arbiter: Adding client with name " + c.getName());
        clientNameMap.put(c.getName(), c);
        inBufferMap.put(c.getName(), new ClientBufferQueue(c.getName()));
        outBufferMap.put(c.getName(),new ClientBufferQueue(c.getName())); // added to fix null ptr exception on first client join
        c.registerArbiter(this);
    }

    public void removeClient(Client c){
        clientNameMap.remove(c.getName());
        inBufferMap.remove(c.getName());
        if (!(c instanceof GUIClient)){
            c.unregisterArbiter();
        }
    }

    public void registerMaze(Maze m){
        assert(m != null);
        this.maze = m;
        maze.addArbiter(this);
    }
}
