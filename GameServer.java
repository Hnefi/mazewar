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
        AtomicInteger root_time_counter = new AtomicInteger(0);

        /* Keep a list of all of the sockets that the arbiter thread 
         * will need to iterate over. */
        List<Socket> listOfClients = Collections.synchronizedList(new ArrayList<Socket>());

        boolean listening = true;

        ServerSocket serverSocket = null; // need to create this.

        // Startup protocol. This server should only take the port it is listening on.
        try {
            if(args.length == 1) {
                /* Set our variables, create sockets when the clients connect. */
                serverSocket = new ServerSocket( (lookup_port = Integer.parseInt(args[0])) );
                eventQ = new ArrayBlockingQueue<GamePacket>(50);
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
         * to remove elements from the event queue and send them to all of the connected clients. */

        new ServerArbiter(root_time_counter,eventQ,listOfClients).start();

        while (listening) {
            Socket new_client = serverSocket.accept();
            if ( listOfClients.add(new_client) )
                new GameServerClientThread(new_client,eventQ).start();
            else
                System.err.println("Unable to add new connected client to list of game players!!!!");

        }

        serverSocket.close();
    }
}
