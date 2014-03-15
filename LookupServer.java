import java.net.*;
import java.io.*;
import java.util.*;

public class LookupServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = null;
        
        boolean listening = true;

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
        	new Thread(new LookupServerHandlerThread(serverSocket.accept(), registry_db)).start();
        }

        serverSocket.close();
    }
}
