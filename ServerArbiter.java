import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/* This class is a worker that constantly blocks on the queue of events.
 * When it is woken up, it simply removes each of those events from the 
 * queue monotonically, tags them with a monotonically increasing int,
 * and spams them out to all the connected clients.
 */
public class ServerArbiter extends Thread {
    private AtomicInteger time_gen;
    private ArrayBlockingQueue<GamePacket> event_queue;
    private List<Socket> list_of_clients;

    public ServerArbiter(AtomicInteger timestamp_er,
                         ArrayBlockingQueue<GamePacket> eventQ,
                         List<Socket> listOfClients) {
       this.time_gen = timestamp_er;
       this.event_queue = eventQ;
       this.list_of_clients = listOfClients;
    }

    @Override 
    public void run()
    {
        /* Whenever there is something in the event queue, retrieve it,
         * tag with an increasing timestamp, and spam it out to all of 
         * the listeners that are currently connected. */
        while ( !Thread.currentThread().isInterrupted() ) {
            try {
                GamePacket to_send = event_queue.take(); // blocking

                /* While we want to send this, we MUST manually synch when
                 * iterating.
                 */
                synchronized(this.list_of_clients) {
                    Iterator<Socket> i = this.list_of_clients.iterator();
                    while(i.hasNext()) {
                        try {
                            System.out.println("Arbiter making output stream.....");
                            ObjectOutputStream toClient = new ObjectOutputStream(i.next().getOutputStream());
                            to_send.tstamp = time_gen.getAndIncrement();
                            System.out.println("Arbiter sending.....");
                            toClient.writeObject(to_send);
                        } catch (IOException x) {
                            System.err.println("Couldn't open output stream in ServerArbiter thread: " + x.getMessage());
                        }
                    }
                }
            } catch (InterruptedException x ) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Server arbiter thread exiting.");
        }
    }
}
