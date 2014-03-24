import java.net.*;
import java.io.*;
import java.util.*;

public class LookupServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = null;

        boolean listening = true;
        int player_id = 0;
        Integer random_seed = null;
        try {
            if(args.length == 2) {
                serverSocket = new ServerSocket(Integer.parseInt(args[0]));
                random_seed = Integer.parseInt(args[1]);
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
            new Thread(new LookupServerHandlerThread(serverSocket.accept(), registry_db,++player_id,random_seed)).start();
        }
        serverSocket.close();
    }
}
