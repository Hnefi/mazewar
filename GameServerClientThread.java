import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class GameServerClientThread extends Thread {
    private Socket socket = null;
    private String client_addr = null;
    private ArrayBlockingQueue<GamePacket> event_queue;
    private ArrayBlockingQueue<GamePacket> join_queue;
    private ConcurrentHashMap<String,SendBuf>map_of_buffers;
    private AtomicInteger which_queue;

    public GameServerClientThread(Socket socket,ArrayBlockingQueue<GamePacket> eventQ,
                                  ArrayBlockingQueue<GamePacket> joinQ,
                                  ConcurrentHashMap<String,SendBuf>bufMap,
                                  AtomicInteger i) {
        super("GameServerClientThread");
        this.socket = socket;
        this.client_addr = this.socket.getRemoteSocketAddress().toString();
        this.event_queue = eventQ;
        this.join_queue = joinQ;
        this.map_of_buffers = bufMap;
        this.which_queue = i;
        System.out.println("Created new Thread to handle client connection with client address: " + this.client_addr);
    }


    @Override
    public void run() {

        boolean gotByePacket = false;

        try {
            /* stream to read from client */
            ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());
            //ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());

            GamePacket packetFromClient;

            while (( packetFromClient = (GamePacket) fromClient.readObject()) != null && !Thread.currentThread().isInterrupted() ) {
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
                }

                /* Otherwise, simply process the event message and enqueue it. */

                GamePacket to_queue = new GamePacket();
                to_queue.type = packetFromClient.type;
                to_queue.player_name = packetFromClient.player_name;
                if(packetFromClient.john_doe != null) {
                    // then there was a killer (but not a secret one)
                    to_queue.john_doe = packetFromClient.john_doe;
                } 
                to_queue.request = packetFromClient.request^true; // inv

                enqueue_event(to_queue);

                /* Sending an BROKER_NULL || BROKER_BYE means quit */
                if (packetFromClient.type == GamePacket.CLIENT_NULL)
                    /* if code comes here, there is an error in the packet */
                    System.err.println("ERROR: Null Client* packet!!");
                System.exit(-1);
                break;
            }
            /* cleanup when client exits */
            fromClient.close();
            socket.close();

        } catch (IOException e) {
            System.out.println("IOException in GameServerClientThread "+e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFoundException! "+e.getMessage());
        }
        

        System.out.println("Thread exiting for client "+client_addr);
    }

    private void enqueue_event(GamePacket p) {
        /* Goes into the blocking queue and places this event in it. */
        try {
            if( which_queue.get() == 0 ) /* If the which_queue AtomicInteger is 0, put into the event q */
                this.event_queue.put(p);
            else
                this.join_queue.put(p);
        } catch (InterruptedException e) {
            // if the parent kills this thread, propagate this interrupt
            // up the stream so the run() method can periodically check
            Thread.currentThread().interrupt();
        } catch (Exception x) {
            // other non-interrupt exceptions
            System.err.println("Exception " + x.getMessage() + "thrown when trying to add GamePacket to queue.");
        }
    }
}
