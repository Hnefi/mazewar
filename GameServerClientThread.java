import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class GameServerClientThread extends Thread {
    private final int seed = 42;
    private Socket socket = null;
    private String client_addr = null;
    private ArrayBlockingQueue<GamePacket> event_queue;
    private ArrayBlockingQueue<GamePacket> join_queue;
    private ConcurrentHashMap<String,SendBuf> map_of_buffers;

    public GameServerClientThread(Socket socket,ArrayBlockingQueue<GamePacket> eventQ,
            ArrayBlockingQueue<GamePacket> joinQ,
            ConcurrentHashMap<String,SendBuf>bufMap)
    {
        super("GameServerClientThread");
        this.socket = socket;
        this.client_addr = this.socket.getRemoteSocketAddress().toString();
        this.event_queue = eventQ;
        this.join_queue = joinQ;
        this.map_of_buffers = bufMap;
        System.out.println("Created new Thread to handle client connection with client address: " + this.client_addr);
    }


    @Override
    public void run() {
        try {
            /* stream to read from client */
            ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());

            GamePacket packetFromClient;

            while (( packetFromClient = (GamePacket) fromClient.readObject()) != null && isInterrupted() == false ) {
                /* Special case - first packet that comes in needs to create a sender thread and buffer
                 * for that sender. */
                if (packetFromClient.type == GamePacket.FIRST_CONNECT) {

                    Socket new_send_sock = new Socket(socket.getInetAddress(),packetFromClient.port);
                    // Now add a new buffer that corresponds to each sender's "events" which it is to distribute
                    SendBuf new_send_buf = new SendBuf(1);
                    Thread new_sender_thread = new GameServerSenderThread(new_send_sock,new_send_buf);

                    // Map this client name to the sender buffer.
                    map_of_buffers.putIfAbsent(packetFromClient.player_name,new_send_buf);
                    // Start sender thread.
                    new_sender_thread.start();

                    /* Now need to pull a deterministic random seed out and send it to the client (place in
                     * the sender buffer) */
                    GamePacket seed_sender = new GamePacket();
                    seed_sender.type = GamePacket.SET_RAND_SEED;
                    seed_sender.seed = this.seed;

                    new_send_buf.putInBuf(seed_sender); // this should wake the sender

                    continue;
                } else if (packetFromClient.type == GamePacket.CLIENT_LEFT) { // our player left. die!!!!
                    /* First of all we need to put this in the event queue as usual,
                     * and then we know that our client is gone so we can close the socket and die. */
                    //System.out.println("Receiver for player: " + packetFromClient.player_name + " is putting its last packet in the event queue and then going off to die.");
                    packetFromClient.request ^= true; // inv direction
                    enqueue_event(packetFromClient);
                    break;
                } else if (packetFromClient.type == GamePacket.CLIENT_NULL) {
                    /* if code comes here, there is an error in the packet */
                    //System.err.println("ERROR: Null Client* packet!!");
                    break;
                }

                //System.out.println("Receiver thread for player " + packetFromClient.player_name + " got packet of type " + packetFromClient.type);

                /* Otherwise, simply process the event message and enqueue it. */

                GamePacket to_queue = new GamePacket();
                to_queue.type = packetFromClient.type;
                to_queue.player_name = packetFromClient.player_name;
                if(packetFromClient.john_doe != null) {
                    to_queue.john_doe = packetFromClient.john_doe;
                } 
                to_queue.request = packetFromClient.request^true; // inv
                to_queue.location = packetFromClient.location;

                enqueue_event(to_queue);

                
            }
            /* cleanup when client exits */
            System.out.println("Receiver thread exiting for client " + socket.toString());
            fromClient.close();
            toClient.close();
            socket.close();

        } catch (IOException e) {
            System.out.println("IOException in GameServerClientThread "+e.toString());
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFoundException! "+e.toString());
        }
    }

    private void enqueue_event(GamePacket p) {
        /* Goes into the blocking queue and places this event in it. */
        try {
            /* Checks for certain packet types. Put anything pertaining
             * to client join into the JOIN queue. */
            int ptype = p.type;
            if (ptype == GamePacket.LOCATION_REQ ||
                    ptype == GamePacket.LOCATION_RESP ||
                    ptype == GamePacket.REMOTE_LOC ||
                    ptype == GamePacket.ALL_LOC_DONE) {
                //System.out.println("Putting object with player name  " + p.player_name + " and type " + p.type + " in join queue.");
                this.join_queue.put(p);
                    }
            else {
                //System.out.println("Putting object with player name " + p.player_name + " and type " + p.type + " in event queue.");
                this.event_queue.put(p);
            }
        } catch (InterruptedException e) {
            // if the parent kills this thread, propagate this interrupt
            // up the stream so the run() method can periodically check
            interrupt();
        } catch (Exception x) {
            // other non-interrupt exceptions
            System.err.println("Exception " + x.getMessage() + "thrown when trying to add GamePacket to queue.");
        }
    }
}
