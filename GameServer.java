import java.net.*;
import java.io.*;
import java.util.*;

public class GameServer {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        //String lookup_host = null;
        int lookup_port = -1;
        String my_name = "GameServer";

        boolean listening = true;

        Socket lookupSocket = null;

        // Startup protocol. This server should only take the port it is listening on.
        if(args.length == 1) {
            /* Set our variables, create sockets when the clients connect. */
            lookup_port = Integer.parseInt(args[0]);
        } else {
            System.err.println("ERROR: Invalid arguments!");
            System.exit(-1);
        }

        /*Now that we've registered, load up the stocks*/
        DB stock_db = new DB();
        stock_db.load_db_from_file(my_name);
        while (listening) {
            new BrokerServerHandlerThread(serverSocket.accept(), stock_db, lookupIn, lookupOut).start();
        }

        stock_db.write_db_to_file();
        lookupOut.close();
        lookupIn.close();
        lookupSocket.close();
        serverSocket.close();
    }
}
