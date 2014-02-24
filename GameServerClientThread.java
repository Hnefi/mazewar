import java.net.*;
import java.io.*;
import java.util.concurrent.*;


public class GameServerClientThread extends Thread {
    private Socket socket = null;
    private String client_addr = null;
    private final ArrayBlockingQueue<GamePacket> event_queue;

    public GameServerClientThread(Socket socket,ArrayBlockingQueue<GamePacket> event_queue) {
        super("GameServerClientThread");
        this.socket = socket;
        this.client_addr = this.socket.getRemoteSocketAddress().toString();
        System.out.println("Created new Thread to handle client connection with client address: " + this.client_addr);
    }

    @Override
    public void run() {

        boolean gotByePacket = false;

        /* stream to read from client */
        ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());
        try {
            GamePacket packetFromClient;

            while (( packetFromClient = (GamePacket) fromClient.readObject()) != null && !Thread.currentThread().isInterrupted() ) {
                /* Simply process the event message and enqueue it. */

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
        } catch (IOException e) {
            System.out.println("IOException! "+e.getMessage());
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFoundException! "+e.getMessage());
        }
        /* cleanup when client exits */
        fromClient.close();
        socket.close();


        System.out.println("Thread exiting for client "+client_addr);
    }

    private void enqueue_event(GamePacket p) {
        /* Goes into the blocking queue and places this event in it. */
        try {
            this.event_queue.put(p);
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
