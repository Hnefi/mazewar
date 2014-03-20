import java.net.*;
import java.io.*;
import java.util.*;
import java.concurrent.*;

/* Internal class which represents ONE of the clients that is
 * the "entry point" for the token ring. It handles putting the
 * new client in the ring.
 */
class EntryPointClient {
    AddressPortPair entryPoint_client = null;

    EntryPointClient(AddressPortPair ep) {
        this.entryPoint_client = ep;
    }

    public synchronized AddressPortPair get {
        return this.entryPoint_client;
    }

    public synchronized void set(AddressPortPair new_client) { 
        entryPoint_client = new_client;
    }
}

public class LookupServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = null;
        EntryPointClient ep = null;

        boolean listening = true;
        int player_id = 0;

        try {
            if(args.length == 1) {
                serverSocket = new ServerSocket(Integer.parseInt(args[0]));
            } else {
                System.err.println("ERROR: Invalid arguments!");
                System.exit(-1);
            }
        } catch (IOException e) {
            System.err.println("ERROR: Could not listen on port!");
            System.exit(-1);
        }

        DNS_DB registry_db = new DNS_DB();


        while (listening) {
            Socket new_sock = serverSocket.accept();
            new Thread(new LookupServerHandlerThread(new_sock, registry_db,ep,++player_id)).start();
            if (num_players == 0) {
                // set the default entry point client

                ep = new EntryPointClient();
            }
        }
        serverSocket.close();
    }
}
