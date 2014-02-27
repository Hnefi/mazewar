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
                if (to_send.type == GamePacket.DIE) {
                    // this is a signal that our connected player left the game, so don't send anything,
                    // die, and clean up.
                    //System.out.println("Sender for player: " + to_send.player_name + " got signalled that it is to go off and die since the player left. Don't write to socket, instead clean up and die. ");
                    toClient.writeObject(to_send);
                    break;
                }

                /* If we get something, then send that shiz */
                //System.out.println("Sender thread writing GamePacket of type " +to_send.type + " to player name " + to_send.player_name);
                toClient.writeObject(to_send);
            }
            /* cleanup when client exits */

            System.out.println("Sender thread exiting for client : " + socket.toString() );
            fromClient.close();
            toClient.close();
            socket.close();

        } catch (IOException e) {
            System.out.println("IOException in GameServerSenderThread "+e.getMessage());
        }

    }
}
