import java.net.*;
import java.io.*;
import java.util.*;

public class BrokerExchange {
    public static void main(String[] args) throws IOException, ClassNotFoundException {

        Socket brokerSocket = null;
        ObjectOutputStream out = null;
        ObjectInputStream in = null; 
	try {
	/* variables for hostname/port */
	    String hostname = "localhost";
	    int port = 4444;
		
	    if(args.length == 2 ) {
	        hostname = args[0];
		port = Integer.parseInt(args[1]);
	    } else {
		System.err.println("ERROR: Invalid arguments!");
		System.exit(-1);
	    }
	        brokerSocket = new Socket(hostname, port);

	    out = new ObjectOutputStream(brokerSocket.getOutputStream());
	    in = new ObjectInputStream(brokerSocket.getInputStream());

	} catch (UnknownHostException e) {
	    System.err.println("ERROR: Don't know where to connect!!");
	    System.exit(1);
	} catch (IOException e) {
	    System.err.println("ERROR: Couldn't get I/O for the connection.");
	    System.exit(1);
	}

        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
        String userInputRaw;
        String userCmd;
        String userSymbol;
        Long userQuote;
        System.out.println("Enter queries or quit for exit:");
            System.out.print(">");
        while ((userInputRaw = stdIn.readLine()) != null
                && !userInputRaw.equals("x")) {
            /*parse the user input for one of the three supported commands*/
            if (!userInputRaw.matches("(add|remove|update) [A-Za-z]+( [0-9]+)?")){
                System.out.println("Invalid command "+userInputRaw);
                System.out.print(">");
                continue;
            }

            /*Parse for the command components*/
            String[] pieces = userInputRaw.split(" ");
            userCmd = pieces[0];
            userSymbol = pieces[1];
            userQuote = Long.valueOf(-1);
            if (pieces.length == 3){
                if(userCmd.equals("update")){  
                    userQuote = Long.parseLong(pieces[2]);
                } else {
                    System.out.println("Invalid command "+userInputRaw);
                    System.out.print(">");
                    continue;
                }
            }
            if (userCmd.equals("update") && userQuote == Long.valueOf(-1)){
                System.out.println("Invalid command "+userInputRaw);
                System.out.print(">");
                continue;
            }

            /*Make a packet and send to the Broker*/
            BrokerPacket packetToServer = new BrokerPacket();
            packetToServer.symbol = userSymbol.toLowerCase();
            if (userCmd.equals("add")){
                packetToServer.type = BrokerPacket.EXCHANGE_ADD;
            } else if (userCmd.equals("remove")){
                packetToServer.type = BrokerPacket.EXCHANGE_REMOVE;
            } else if (userCmd.equals("update")){
                packetToServer.type = BrokerPacket.EXCHANGE_UPDATE;
                packetToServer.quote = userQuote;
            } else {
                System.out.println("Invalid command "+userInputRaw);
                System.out.print(">");
                continue;
            }
            out.writeObject(packetToServer);

            /*get server reply and handle the error cases*/
	    BrokerPacket packetFromServer;
	    packetFromServer = (BrokerPacket) in.readObject();

            if (packetFromServer.type != BrokerPacket.EXCHANGE_REPLY){
                System.out.println("Something funky's going on!");
                System.out.print(">");
                continue;
            }

            if (packetFromServer.error_code == BrokerPacket.ERROR_INVALID_SYMBOL){
                System.out.println(userSymbol+" invalid.");
            } else if (packetFromServer.error_code == BrokerPacket.ERROR_OUT_OF_RANGE){
                System.out.println(userSymbol+" out of range.");
            } else if (packetFromServer.error_code == BrokerPacket.ERROR_SYMBOL_EXISTS){
                System.out.println(userSymbol+" exists.");
            } else {
                if (userCmd.equals("add")){
                    System.out.println(userSymbol+" added.");
                } else if (userCmd.equals("remove")){
                    System.out.println(userSymbol+" removed.");
                } else if (userCmd.equals("update")){
                    System.out.println(userSymbol+" updated to "+packetFromServer.quote+".");
                }
            }
            
            System.out.print(">");
        }
        
	/* tell server that i'm quitting */

	BrokerPacket packetToServer = new BrokerPacket();
	packetToServer.type = BrokerPacket.BROKER_BYE;
	out.writeObject(packetToServer);

	out.close();
	in.close();
	stdIn.close();
	brokerSocket.close();
    }
}
