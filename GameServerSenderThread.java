import java.net.*;
import java.io.*;
import java.util.concurrent.*;


public class GameServerSenderThread extends Thread {
    private Socket socket = null;
    private SendBuf my_send_buffer = null;

    public GameServerSenderThread(Socket socket,SendBuf myBuffer) {
        super("GameServerSenderThread");
        this.socket = socket;
        this.my_send_buffer = myBuffer;
        System.out.println("Created new sender thread to handle client connection with socket identification: " + socket.toString() );
    }

    @Override
    public void run() {

        try {
            /* stream to write to client */
            ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream fromClient = new ObjectInputStream(socket.getInputStream());

            while ( isInterrupted()== false ) {
                GamePacket to_send = my_send_buffer.takeFromBuf();

                /* If we get something, then send that shiz */
                System.out.println("Sender thread writing GamePacket of type " +to_send.player_name + " to player name " + to_send.player_name);
                toClient.writeObject(to_send);
            }
            /* cleanup when client exits */
            System.out.println("Server sender thread exiting and closing sockets.....");
            toClient.close();
            socket.close();

        } catch (IOException e) {
            System.out.println("IOException in GameServerSenderThread "+e.getMessage());
        }

        System.out.println("Thread exiting for client sender thread with information: " + socket.toString() );
    }
}
