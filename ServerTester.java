import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

class Receiver implements Runnable
{
    ServerSocket l_sock;
    Exchanger<ArrayList> x_point;
    ArrayList<GamePacket> to_get;
    public Receiver(ServerSocket s,Exchanger<ArrayList> x)
    {
        this.l_sock = s;
        this.x_point = x;
        to_get = new ArrayList<GamePacket>();
    }

    public void run()
    {
        /* Block until we get a new connection from the server */
        Socket my_sock = null;
        try {
            my_sock = l_sock.accept();
        } catch (IOException consumed) {
            System.err.println("Receiver couldn't open socket.");
        }

        System.out.println("Receiver thread got new socket.");

        /* Open socket and listen on it for 5 GamePackets */
        int num_received = 0;
        ObjectInputStream fromServ = null;
        ObjectOutputStream toServ = null;
        try {
            toServ = new ObjectOutputStream(my_sock.getOutputStream());
            fromServ = new ObjectInputStream(my_sock.getInputStream());
        } catch (IOException x) {
            System.err.println("Receiver couldn't open input stream with message: " + x.getMessage());
        }
        System.out.println("Receiver thread got new Input stream successfully.");
        while (num_received < 6) {
            GamePacket received;
            try {
                received = (GamePacket) fromServ.readObject();
                System.out.println("Receiver got packet with player name: " + received.player_name + "type: " + received.type);
                to_get.add(received); 
                num_received++;
            } catch (IOException x) {
                System.err.println("Receiver missed reading packet!!");
                num_received++;
            } catch (ClassNotFoundException cnf) {
                System.err.println("Object doesn't match GamePacket.");
            }
        }
        ArrayList<GamePacket> before_send = null; 
        try {
            before_send = x_point.exchange(to_get);
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Receiver thread done!!!!!!");
    }
}

class Sender implements Runnable
{
    Socket my_sock;
    int my_port;
    Exchanger<ArrayList> x_point;
    ArrayList<GamePacket> to_send;
    public Sender(Socket s,Exchanger<ArrayList> x,int port)
    {
        this.my_sock = s;
        this.x_point = x;
        this.my_port = port;
    }

    public void run()
    {
        /* Open sockets */
        ObjectOutputStream toServ = null;
        ObjectInputStream fromServ = null;
        try {
            toServ = new ObjectOutputStream(my_sock.getOutputStream());
            fromServ = new ObjectInputStream(my_sock.getInputStream());
        } catch (IOException x) {
            System.err.println("Sender couldn't open streams.");
        }

        System.out.println("Sender trying to write FCON.");

        /* Send a FIRST_CONNECT so that the receiver thread can open its communication. */
        GamePacket fcon = new GamePacket();
        fcon.type = GamePacket.FIRST_CONNECT;
        fcon.port = my_port;
        fcon.player_name = "mark" + String.valueOf(my_port);
        try {
            toServ.writeObject(fcon);
        } catch (IOException x) {
            System.err.println("Sender couldn't write FCON.");
        }
        System.out.println("Sender thread wrote FCON packet.");

        // initialize array of game packets
        to_send = new ArrayList<GamePacket>(5);
        ArrayList<GamePacket> before_send = new ArrayList<GamePacket>();
        for(int i = 0;i<6;i++) {
            System.out.println(i);
            GamePacket tmp = new GamePacket();
            tmp.player_name = "mark" + String.valueOf(my_port);
            tmp.request = true;
            switch(i) {
                case 5:
                    tmp.type = GamePacket.CLIENT_LEFT;
                    break;
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
            // put into queue;
            System.out.println("Adding to the list...");
            to_send.add(tmp);
            before_send.add(tmp);
            System.out.println("Sender thread wrote packet " + i);
            try {
                toServ.writeObject(tmp);
            } catch (IOException x) {
                System.err.println("Sender thread couldn't send packet" + i); 
            }
        }

        ArrayList<GamePacket> after_receive = null;
        try {
            after_receive = x_point.exchange(before_send);
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
        }
        Iterator<GamePacket> i = after_receive.iterator();
        while(i.hasNext()) {
            GamePacket p = i.next();
            System.out.println("Packet type in after_receive: " + p.type + " with name: " + p.player_name);
        }

        System.out.println("Sender thread success!!!!");
    }
}

public class ServerTester {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        String server_to_chk = null;
        int server_port = -1;
        int my_port = -1;
        String my_name = "Tester";

        Socket outgoing_sock = null;
        ServerSocket listening_sock = null;
        Exchanger x_point = null;

        /* SETUP: Takes in the server, the port it listens on, as well as
         * the port that WE are listening on. 
         */
        try {
            if(args.length == 3) {
                /* Set our variables for the server and its port */
                server_to_chk = args[0];
                server_port = Integer.parseInt(args[1]);
                my_port = Integer.parseInt(args[2]);
                listening_sock = new ServerSocket(my_port);
                outgoing_sock = new Socket(server_to_chk,server_port);

                x_point = new Exchanger<ArrayList>();

            } else {
                System.err.println("ERROR: Invalid arguments!");
                System.exit(-1);
            }
        } catch (Exception x) {
            System.err.println("ERROR: Exception " + x.toString()+ " thrown on attempting to open sockets.");
            System.exit(-1);
        }

        // All this thing does is spawn a couple new sender/receiver
        // Runnables and set them into motion. 
        System.out.println("Setting tester threads in motion");
        Thread send_thread = new Thread(new Sender(outgoing_sock,x_point,my_port));
        Thread recv_thread = new Thread(new Receiver(listening_sock,x_point));
        System.out.println("Starting threads");
        recv_thread.start();
        send_thread.start();

    }
}
