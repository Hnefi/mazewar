import java.net.*;
import java.io.*;
import java.util.*;


public class BrokerServerHandlerThread extends Thread {
	private Socket socket = null;
        private DB stock_db = null;
        private ObjectInputStream fromLookup = null;
        private ObjectOutputStream toLookup = null;
        private String client_addr = null;

	public BrokerServerHandlerThread(Socket socket, DB stock_db, ObjectInputStream in, ObjectOutputStream out) {
		super("BrokerServerHandlerThread");
		this.socket = socket;
                this.stock_db = stock_db;
		this.fromLookup = in;
                this.toLookup = out;
                System.out.println("Created new Thread to handle client");
                this.client_addr = this.socket.getRemoteSocketAddress().toString();
                System.out.println("Remote address: "+client_addr);
	}

        private Long forward_request_for_symbol(String symbol){
            //input: a symbol which was not found in the local stock database
            //output; a quote for that stock if it exists in any other registered broker, null otherwise
            //Process: Get a socket for every broker except this one and send each one a BROKER_FORWARD request
            Long return_quote = null;


            try {
                BrokerPacket toLookupPacket = new BrokerPacket(); //we need to know where the other brokers are
                toLookupPacket.type = BrokerPacket.BROKER_FORWARD;
                toLookupPacket.exchange = stock_db.name;

                toLookup.writeObject(toLookupPacket);

                BrokerPacket fromLookupPacket = (BrokerPacket) fromLookup.readObject();
                if (fromLookupPacket != null){
                    int num_other_brokers = fromLookupPacket.num_locations;
                    for(int i = 0; i < num_other_brokers; ++i){
                        String broker_address = fromLookupPacket.locations[i].broker_host;
                        Integer broker_port = fromLookupPacket.locations[i].broker_port;
        
                        Socket brokerSocket = new Socket(broker_address, broker_port);

                        ObjectOutputStream toBroker = new ObjectOutputStream(brokerSocket.getOutputStream());
                        ObjectInputStream fromBroker = new ObjectInputStream(brokerSocket.getInputStream());

                        BrokerPacket packetToBroker = new BrokerPacket();
                        packetToBroker.type = BrokerPacket.BROKER_FORWARD;
                        packetToBroker.symbol = symbol;

                        //In theory we should write all the objects to all the brokers first, and then read the objects
                        //afterwards to prevent the blocking reads from taking forever here... but that's too much overhead for now.
                        //For a small number of brokers reading/writing immediately should be fine.
                        toBroker.writeObject(packetToBroker);

                        BrokerPacket packetFromBroker = (BrokerPacket) fromBroker.readObject();

                        BrokerPacket byePacket = new BrokerPacket();
                        byePacket.type = BrokerPacket.BROKER_BYE;
                        toBroker.writeObject(byePacket);

                        fromBroker.close();
                        toBroker.close();
                        brokerSocket.close();

                        if (packetFromBroker.type == BrokerPacket.BROKER_QUOTE && packetFromBroker.quote != null){
                            return_quote = packetFromBroker.quote;
                            break;
                        } else if (packetFromBroker.type == BrokerPacket.BROKER_ERROR && packetFromBroker.error_code == BrokerPacket.ERROR_INVALID_SYMBOL) {
                            continue;
                        } else {
                            System.err.println("Something bad has happened!");
                            System.exit(-1);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("IOException! "+e.getMessage());
	    } catch (ClassNotFoundException e) {
                System.out.println("ClassNotFoundException! "+e.getMessage());
	    }
            return return_quote;
        }

	public void run() {

		boolean gotByePacket = false;
		
		try {
			/* stream to write back to client */
			ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());
			
                        /* stream to read from client */
			ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());
			BrokerPacket packetFromClient;

			while (( packetFromClient = (BrokerPacket) fromClient.readObject()) != null) {
				/* create a packet to send reply back to client */
				BrokerPacket packetToClient = new BrokerPacket();
				
				/* process message */
				/* just echo in this example */
                                boolean send_packet = false;
				if(packetFromClient.type == BrokerPacket.BROKER_REQUEST || packetFromClient.type == BrokerPacket.BROKER_FORWARD) {
				        packetToClient.type = BrokerPacket.BROKER_QUOTE;
                                        send_packet = true;
					String stock_name = packetFromClient.symbol.toLowerCase();
				        packetToClient.error_code = BrokerPacket.BROKER_NULL;

                                        if (stock_db.stocks.containsKey(stock_name)){
                                            packetToClient.quote = stock_db.stocks.get(stock_name);
                                        }
                                        else if (packetFromClient.type != BrokerPacket.BROKER_FORWARD) {
                                            Long quote = forward_request_for_symbol(stock_name);
                                            if (quote == null){
                                                packetToClient.type = BrokerPacket.BROKER_ERROR;
                                                packetToClient.error_code = BrokerPacket.ERROR_INVALID_SYMBOL;
                                            } else {
                                                packetToClient.quote = quote;
                                            }
                                        } else {
                                            //if you were forwarded to, don't forward again!
                                            packetToClient.type = BrokerPacket.BROKER_ERROR;
                                            packetToClient.error_code = BrokerPacket.ERROR_INVALID_SYMBOL;
                                        }
				} else if (packetFromClient.type == BrokerPacket.EXCHANGE_ADD) {
                                        send_packet = true;
                                        packetToClient.type = BrokerPacket.EXCHANGE_REPLY;
                                        if (stock_db.stocks.containsKey(packetFromClient.symbol)){
                                            packetToClient.error_code = BrokerPacket.ERROR_SYMBOL_EXISTS;
                                        } else {
                                            stock_db.stocks.put(packetFromClient.symbol, Long.valueOf(0));
                                            packetToClient.error_code = BrokerPacket.BROKER_NULL;
                                        }
				} else if (packetFromClient.type == BrokerPacket.EXCHANGE_REMOVE) {
                                        send_packet = true;
                                        packetToClient.type = BrokerPacket.EXCHANGE_REPLY;
                                        if (stock_db.stocks.containsKey(packetFromClient.symbol)){
                                            stock_db.stocks.remove(packetFromClient.symbol);
                                            packetToClient.error_code = BrokerPacket.BROKER_NULL;
                                        } else {
                                            packetToClient.error_code = BrokerPacket.ERROR_INVALID_SYMBOL;
                                        }
				} else if (packetFromClient.type == BrokerPacket.EXCHANGE_UPDATE) {
                                        send_packet = true;
                                        packetToClient.type = BrokerPacket.EXCHANGE_REPLY;
                                        if (stock_db.stocks.containsKey(packetFromClient.symbol)){
                                            if (packetFromClient.quote >= 0 && packetFromClient.quote <= 300){
                                                stock_db.stocks.put(packetFromClient.symbol, packetFromClient.quote);
                                                packetToClient.quote = packetFromClient.quote;
                                                packetToClient.error_code = BrokerPacket.BROKER_NULL;
                                            } else {
                                                packetToClient.error_code = BrokerPacket.ERROR_OUT_OF_RANGE;
                                            }
                                        } else {
                                            packetToClient.error_code = BrokerPacket.ERROR_INVALID_SYMBOL;
                                        }
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


                        /*Save changes to the database*/		
                        stock_db.write_db_to_file();
	
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
                
                System.out.println("Thread exiting for client "+client_addr);
	}
}
