import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

class Receiver implements Runnable
{
    ServerSocket l_sock;
    ArrayBlockingQueue<GamePacket> in_q;
    public Receiver(ServerSocket s,ArrayBlockingQueue<GamePacket> q)
    {
        this.l_sock = s;
        this.in_q = q;
    }

    public void run()
    { 
        ObjectOutputStream toClient = null;
        ObjectInputStream fromClient = null;
        Socket socket = null;
        try{
            /* First thing is to wait until the server gives us a new connected two-way socket */
            socket = l_sock.accept();

            /* stream to read from client */
            toClient = new ObjectOutputStream(socket.getOutputStream());
            fromClient = new ObjectInputStream(socket.getInputStream());

            GamePacket packetFromClient;

            while (( packetFromClient = (GamePacket) fromClient.readObject()) != null && Thread.currentThread().isInterrupted() == false ) {
                try {
                    in_q.put(packetFromClient);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
            }

            /* cleanup when client exits */
            fromClient.close();
            toClient.close();
            socket.close();
        } catch (IOException x) {
            System.err.println("IOException: " + x.toString() + " in receiver thread");
        } catch (ClassNotFoundException x) {
            System.err.println("Read object got wrong object type.");
        }
        System.out.println("Receiver thread done!!!!!!");
    }
}

class Sender implements Runnable
{
    Socket my_sock;
    int my_port;
    ArrayBlockingQueue<GamePacket> out_q;
    public Sender(Socket s,int port,ArrayBlockingQueue<GamePacket> q)
    {
        this.my_sock = s;
        this.my_port = port;
        this.out_q = q;
    }

    public void run()
    {
        ObjectOutputStream toClient = null;
        ObjectInputStream fromClient = null;
        try{
            /* stream to write to client */
            toClient = new ObjectOutputStream(my_sock.getOutputStream());
            fromClient = new ObjectInputStream(my_sock.getInputStream());

            while ( Thread.currentThread().isInterrupted() == false ) {
                System.out.println("Sender trying to take...");
                GamePacket to_send = null;
                try {
                    to_send = out_q.take();
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }

                /* If we get something, then send that shiz */
                System.out.println("Sender thread writing GamePacket to player name " + to_send.player_name);
                toClient.writeObject(to_send);
            }

            /* cleanup when client exits */
            toClient.close();
            fromClient.close();
            my_sock.close();
        } catch (IOException x) {
            System.err.println("IOExceptioN: " + x.toString() + " in sender thread.");
        }
        System.out.println("Thread exiting for client sender thread with information: " + my_sock.toString() );
    }
}

class MasterThread implements Runnable {
    private int my_port;
    private ArrayBlockingQueue<GamePacket> in_q;
    private ArrayBlockingQueue<GamePacket> out_q;
    private Thread sender;
    private Thread receiver;

    public MasterThread(Socket outgoing_sock,ServerSocket incoming_sock,int port )
    {
        // has to spawn the sub-threads and make the buffers etc
        this.my_port = port;
        this.in_q = new ArrayBlockingQueue<GamePacket>(10);
        this.out_q = new ArrayBlockingQueue<GamePacket>(10);
        sender = new Thread(new Sender(outgoing_sock,my_port,this.out_q));
        receiver = new Thread(new Receiver(incoming_sock,this.in_q));
        sender.start();
        receiver.start();
        System.out.println("Master spawned new threads!!!");

    }

    public void run()
    {
        /* This just manages buffers to test the joining protocol. */
        GamePacket firstcon = new GamePacket();
        firstcon.type = GamePacket.FIRST_CONNECT;
        firstcon.player_name = "mark" + String.valueOf(my_port);
        firstcon.port = my_port;
        send(firstcon);

        GamePacket p = new GamePacket();
        p.player_name = "mark" + String.valueOf(my_port);
        p.request = true;
        p.type = GamePacket.CLIENT_JOINED;

        // now enqueue this packet to signal we are joining
        

        // we should get back a packet with a random seed in it.
        
        //assert (seed_pack.type == GamePacket.SET_RAND_SEED && seed_pack.seed == 42);

    }

    private void send(GamePacket p){
        try { 
            out_q.put(p);
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
        }
    }

    private GamePacket recv() {
        GamePacket p = null;
        try {
            p = in_q.take();
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt();
        }
        return p;
    }
}

public class JoinTester {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        String server_to_chk = null;
        int server_port = -1;
        int my_port = -1;
        String my_name = "Tester";

        Socket outgoing_sock = null;
        ServerSocket listening_sock = null;

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
        new Thread(new MasterThread(outgoing_sock,listening_sock,my_port)).start();
    }
}
