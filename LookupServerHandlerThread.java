import java.net.*;
import java.io.*;
import java.util.*;


public class LookupServerHandlerThread implements Runnable {
	private Socket socket = null;
        private DNS_DB registry_db = null;
        private String client_addr = null;

	public LookupServerHandlerThread(Socket socket, DNS_DB reg_db) {
		super("BrokerServerHandlerThread");
		this.socket = socket;
                this.registry_db = reg_db;
		System.out.println("Created new Thread to handle client");
                this.client_addr = this.socket.getRemoteSocketAddress().toString();
                System.out.println("Remote address: "+client_addr);
	}

	public void run() {

		boolean gotByePacket = false;
		boolean registered_broker = false;
		String registeredExchange = null;

                try {
			/* stream to read from client */
			ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());
			BrokerPacket packetFromClient;
			
			/* stream to write back to client */
			ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());
			boolean clientIsBroker = false;

			while (( packetFromClient = (BrokerPacket) fromClient.readObject()) != null) {
				/* create a packet to send reply back to client */
				BrokerPacket packetToClient = new BrokerPacket();
				packetToClient.type = BrokerPacket.LOOKUP_REPLY;
			        
                                boolean send_packet = false;
                                if(packetFromClient.type == BrokerPacket.LOOKUP_REGISTER &&
                                    packetFromClient.num_locations > 0){
                                    clientIsBroker = true;
                                    send_packet = true;
                                    String regBrokerAddr = socket.getInetAddress().toString();
                                    int regBrokerPort = packetFromClient.locations[0].broker_port.intValue();
                                    registeredExchange = packetFromClient.exchange; //cache the registered broker name so we can remove it from the hash later
                                    registry_db.register_name_and_dest(packetFromClient.exchange, regBrokerAddr, regBrokerPort);
                                } else if (packetFromClient.type == BrokerPacket.LOOKUP_REQUEST) {
                                    send_packet = true;
                                    //return the location of the exchange in the packet
                                    String requested_name = packetFromClient.exchange;
                                    AddressPortPair addr = registry_db.get_socket(requested_name);
                                    if (addr == null){
                                        packetToClient.type = BrokerPacket.ERROR_INVALID_EXCHANGE;
                                    } else {
                                        BrokerLocation l = new BrokerLocation( addr.addr, addr.port );
                                        packetToClient.num_locations = 1;
                                        packetToClient.locations = new BrokerLocation[]{ l };
                                    }
                                } else if (packetFromClient.type == BrokerPacket.BROKER_FORWARD) {
                                    clientIsBroker = true;
                                    send_packet = true;
                                    //return the locations of every other exchange except the one that made the request
                                    String this_exchange = packetFromClient.exchange;
                                    AddressPortPair this_address = registry_db.get_socket(this_exchange);
                                    String this_host = this_address.addr;
                                    int this_port = this_address.port;
                                    ArrayList<AddressPortPair> other_brokers = registry_db.get_all_address_except_for(this_host, this_port);
                                    //now iterate through this and convert to the list needed for the BrokerPacket
                                    int num_other_brokers = other_brokers.size();
                                    packetToClient.locations = new BrokerLocation [num_other_brokers];
                                    for (int i = 0; i < num_other_brokers; ++i){
                                        String host_addr = other_brokers.get(i).addr;
                                        Integer host_port = (Integer)other_brokers.get(i).port;
                                        packetToClient.locations[i] = new BrokerLocation (host_addr, host_port);
                                    }
                                    packetToClient.num_locations = num_other_brokers;
                                }

				if (send_packet){
        				/* send reply back to client */
					toClient.writeObject(packetToClient);
					
					/* wait for next packet */
					continue;
                                }
				/* Sending an BROKER_NULL || BROKER_BYE means quit */
				if (packetFromClient.type == BrokerPacket.BROKER_NULL || packetFromClient.type == BrokerPacket.BROKER_BYE) {
					gotByePacket = true;
					break;
				}
				
				/* if code comes here, there is an error in the packet */
				System.err.println("ERROR: Unknown BROKER_* packet!!");
				System.exit(-1);
			}

                        //if our client is a broker, it is expecting to see a by packet before closing this connection!
        		/* cleanup when client exits */
			fromClient.close();
			toClient.close();
			socket.close();

		} catch (IOException e) {
                        System.out.println("IOException! "+e.getMessage());
			if(!gotByePacket)
				e.printStackTrace();
		} catch (ClassNotFoundException e) {
                        System.out.println("ClassNotFoundException! "+e.getMessage());
			if(!gotByePacket)
				e.printStackTrace();
		}
                
                if (registeredExchange != null){
                    registry_db.remove_exchange(registeredExchange);
                }
                System.out.println("Thread exiting for client "+client_addr);
	}
}
