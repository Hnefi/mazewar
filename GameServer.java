import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class GameServer {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        //String lookup_host = null;
        int lookup_port = -1;
        String my_name = "GameServer";
        ArrayBlockingQueue eventQ = null;
        AtomicInteger root_time_counter = new AtomicInteger(0);

        boolean listening = true;

        ServerSocket serverSocket = null; // need to create this.

        // Startup protocol. This server should only take the port it is listening on.
        try {
            if(args.length == 1) {
                /* Set our variables, create sockets when the clients connect. */
                serverSocket = new ServerSocket( (lookup_port = Integer.parseInt(args[0])) );
                eventQ = new ArrayBlockingQueue(50);
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

        while (listening) {
            new GameServerClientThread(serverSocket.accept(),eventQ);
        }

        /*stock_db.write_db_to_file();
          lookupOut.close();
          lookupIn.close();
          lookupSocket.close();
          serverSocket.close();*/
        serverSocket.close();
    }
}
