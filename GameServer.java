import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class GameServer {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        //String lookup_host = null;
        int lookup_port = -1;
        String my_name = "GameServer";
        ArrayBlockingQueue<GamePacket> eventQ = null;
        ArrayBlockingQueue<GamePacket> joinQ = null;
        //float lfac = (float)0.75;
        ConcurrentHashMap<String,SendBuf> map_of_buffers = new ConcurrentHashMap<String,SendBuf>(32);
        AtomicInteger synch_point = null;


        AtomicInteger root_time_counter = new AtomicInteger(0);

        boolean listening = true;

        ServerSocket serverSocket = null; // need to create this.

        // Startup protocol. This server takes the port that it is listening on
        try {
            if(args.length == 1) {
                /* Set our variables, create sockets when the clients connect. */
                serverSocket = new ServerSocket( (lookup_port = Integer.parseInt(args[0])) );
                eventQ = new ArrayBlockingQueue<GamePacket>(50);
                joinQ = new ArrayBlockingQueue<GamePacket>(10); // smaller because only used for joins/leaves
                synch_point = new AtomicInteger(0);
            } else {
                System.err.println("ERROR: Invalid arguments!");
                System.exit(-1);
            }
        } catch (Exception x) {
            System.err.println("ERROR: Exception " + x.toString()+ " thrown on attempting to start GameServer.");
            System.exit(-1);
        }

        /* Now that we have the port we are listening on, we need to accept any requests
         * that are being sent from clients. Also create a root server thread whose entire job is
         * to remove elements from the event queue and send them to all of the connected clients. 
         * ALSO, add the thread whose sole responsibility is to manage the dynamic join/drop protocol
         */
       
        /* Start master server threads */
        new ServerArbiter(root_time_counter,eventQ,joinQ,map_of_buffers,synch_point).start();
        new ServerJoinDropThread(joinQ,map_of_buffers,synch_point).start();

        while (listening) {
            Socket new_client = serverSocket.accept();
            Thread new_recv_thread = new GameServerClientThread(new_client,eventQ,joinQ,map_of_buffers);
            new_recv_thread.start();
        }
        serverSocket.close();
    }
}
