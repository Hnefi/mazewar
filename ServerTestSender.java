import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ServerTestSender {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        int server_port = -1;
        String my_name = "Tester";
        //ArrayBlockingQueue<GamePacket> eventQ = null;
        //AtomicInteger root_time_counter = new AtomicInteger(0);

        /* Keep a list of all of the sockets that the arbiter thread 
         * will need to iterate over. */
        //List<Socket> listOfClients = Collections.synchronizedList(new ArrayList<Socket>());

        ServerSocket connection_listener = null;
        boolean listening = true;

        // Startup protocol. This server should only take the port it is listening on.
        try {
            if(args.length == 1) {
                /* Set our variables for the server and its port */
                server_port = Integer.parseInt(args[0]);
                connection_listener = new ServerSocket(server_port);
                
            } else {
                System.err.println("ERROR: Invalid arguments!");
                System.exit(-1);
            }
        } catch (Exception x) {
            System.err.println("ERROR: Exception " + x.toString()+ " thrown on attempting to open ServTest socket.");
            System.exit(-1);
        }

        /* Wait for the server to open a connection to us, and begin receiving GamePackets (printing to std out)
         *  - this simulates how the client will have 2 threads, one to send events TO the server and one to
         *    receive events FROM the server. 
         */
        Socket socket_to_server = null;

        socket_to_server = connection_listener.accept(); // only one open socket
        



        int timestamp = -1;
        int prev_stamp = -1;
        for(int i = 0;i<5;i++) {
            GamePacket tmp = new GamePacket();
            tmp.player_name = "Client";
            tmp.request = true;
            switch(i) {
                case 0:
                    tmp.type = GamePacket.CLIENT_MOVED_FORWARD;
                    break;
                case 1:
                    tmp.type = GamePacket.CLIENT_MOVED_BACK;
                    break;
                case 2:
                    tmp.type = GamePacket.CLIENT_INVERT;
                    break;
                case 3:
                    tmp.type = GamePacket.CLIENT_TURN_L;
                    break;
                case 4:
                    tmp.type = GamePacket.CLIENT_TURN_R;
                    break;
            }
            /* Send to server client thread */
            to_serv.writeObject(tmp);
            System.err.println("Sent object number " + i + " to server.");

            /* Wait for reply and check assertions. */

            GamePacket packet_from_server = (GamePacket) from_serv.readObject();
            prev_stamp = timestamp;
            timestamp = packet_from_server.tstamp;
            assert(packet_from_server != null);
            assert(packet_from_server.type == tmp.type);
            assert(packet_from_server.player_name == tmp.player_name);
            assert(packet_from_server.request == false);
            assert(timestamp > prev_stamp);
            System.err.println("Received object with timestamp " + timestamp + " from server.");
        }
    }
}
