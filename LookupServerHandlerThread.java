import java.net.*;
import java.io.*;
import java.util.*;


public class LookupServerHandlerThread implements Runnable {
    private Socket socket = null;
    private DNS_DB registry_db = null;
    private String client_addr = null;
    private final int player_id;
    private final int random_seed;

    public LookupServerHandlerThread(Socket socket, DNS_DB reg_db,int pid,Integer rand) {
        this.socket = socket;
        this.registry_db = reg_db;
        this.player_id = pid;
        this.random_seed = rand.intValue();
        System.out.println("Created new Thread to handle client");
        this.client_addr = this.socket.getRemoteSocketAddress().toString();
        System.out.println("Remote address: "+client_addr);
    }

    public void run() {

        boolean gotByePacket = false;
        boolean registered_broker = false;
        String player_name = null;

        try {
            /* stream to read from client */
            ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());
            GamePacket packetFromClient;

            /* stream to write back to client */
            ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());

            while (( packetFromClient = (GamePacket) fromClient.readObject()) != null ) {
                /* create a packet to send reply back to client */
                GamePacket packetToClient = new GamePacket();

                boolean send_packet = false;
                if(packetFromClient.type == GamePacket.RING_JOIN) {
                    send_packet = true;
                    InetAddress newClientAddr = socket.getInetAddress();
                    // client listens on this port
                    int newClientPort = packetFromClient.port;
                    AddressPortPair new_guy = new AddressPortPair(newClientAddr,newClientPort);
                    registry_db.register_name_and_dest(new_guy);

                    /* Now need to send back the list of all other players */

                    ArrayList<AddressPortPair> ret_list = registry_db.get_clients_except_for(new_guy);
                    
                    packetToClient.list_of_others = ret_list;
                    packetToClient.pid = this.player_id;
                    packetToClient.seed = random_seed;
                } 
                /* Use this code to handle client leave messages. */
                else if (packetFromClient.type == GamePacket.CLIENT_NULL || packetFromClient.type == GamePacket.RING_LEAVE) {
                    if (registry_db.get_num_players() == 1) {
                        packetToClient.type = GamePacket.RING_LAST_PLAYER;
                    } else {
                        packetToClient.type = GamePacket.RING_NOP;
                    }

                    /* Unregister from lookup server. */
                    InetAddress leaving_inet = socket.getInetAddress();
                    registry_db.remove_client(leaving_inet);    
                    send_packet = true; 
                } 
                /* Use to handle closing initial dns lookup connections */
                else if (packetFromClient.type == GamePacket.RING_NOP) {
                    gotByePacket = true;
                    break;
                }
                if (send_packet){
                    /* send reply back to client */
                    toClient.writeObject(packetToClient);

                    /* wait for next packet */
                    continue;
                }

                /* if code comes here, there is an error in the packet */
                System.err.println("ERROR: Unknown GamePacket!!");
                System.exit(-1);
            }

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

        System.out.println("LookupServerHandlerThread exiting for client "+client_addr);
    }
}

