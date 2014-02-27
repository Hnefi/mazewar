import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/* This class is a worker that constantly blocks on the queue of events.
 * When it is woken up, it simply removes each of those events from the 
 * queue atomically, tags them with a monotonically increasing int,
 * and spams them out to all the connected clients.
 */
public class ServerArbiter extends Thread {
    private AtomicInteger time_gen;
    private ArrayBlockingQueue<GamePacket> event_queue;
    private ArrayBlockingQueue<GamePacket> join_queue;
    private ConcurrentHashMap<String,SendBuf> map_of_buffers;
    private final AtomicInteger synch_point;

    public ServerArbiter(AtomicInteger timestamp_er,
            ArrayBlockingQueue<GamePacket> eventQ,
            ArrayBlockingQueue<GamePacket> joinQ,
            ConcurrentHashMap<String,SendBuf> bmap,
            AtomicInteger cdl) {
        super("ServerArbiter");
        this.time_gen = timestamp_er;
        this.event_queue = eventQ;
        this.join_queue = joinQ;
        this.map_of_buffers = bmap;
        this.synch_point = cdl;
        System.out.println("Created the server arbiter thread which pulls events out of the event queue.");
    }

    @Override 
    public void run()
    {
        /* Whenever there is something in the event queue, retrieve it,
         * tag with an increasing timestamp, place it in EACH of the 
         * currently existing send buffers. This wakes the sender threads
         * which then push these packets out to all the connected players
         */
        while ( isInterrupted() == false ) {
            try {
                GamePacket to_send = event_queue.take(); // blocking
                to_send.tstamp = time_gen.getAndIncrement();
                if (to_send.type == GamePacket.CLIENT_JOINED) {
                    // must put this in the join queue to wake the 
                    // thread which manages join/drop
                    join_queue.put(to_send);
                    System.out.println("Saw client join request from new player " + to_send.player_name + " , putting it in the join queue and going to sleep on the barrier.");
                    /* Hand exec to the join/drop thread and block */
                    synchronized (synch_point) {
                        synch_point.set(1); 
                        synch_point.notify();
                        System.out.println("Handing execution to the join/drop and sleeping....");
                        while ( synch_point.get() == 1 ) {
                            try {
                                synch_point.wait(); // blocks here wait on join
                            } catch (InterruptedException x) {
                                interrupt();
                            }
                        }
                    }
                    System.out.println("ServerArbiter re-woken....");
                    continue;
                } else if (to_send.type == GamePacket.CLIENT_LEFT) {
                    /* Do a few things: First, make a new special DIE packet and put that in the proper
                     * buffer. Then sleep for a little while to be reasonably confident that the associated
                     * sender thread died. Then remove that player map from the hashmap, and continue to
                     * broadcast as normal. */
                    GamePacket new_die = new GamePacket();
                    new_die.type = GamePacket.DIE;

                    System.out.println("Taking the buffer which corresponds to " + to_send.player_name + " out of the hashmap.");

                    SendBuf buffer_of_dead_thread = map_of_buffers.remove(to_send.player_name);
                    buffer_of_dead_thread.putInBuf(new_die);
                    
                    try {
                        sleep(100);
                    } catch (InterruptedException x) {
                        interrupt();
                    }
                }
                System.out.println("Client event_queue arbiter took a new object, and generated timestamp " + to_send.tstamp + " . Now placing it into all of the send buffers.");
                /* Now get an iterator over the hashmap and place this
                 * object in EACH send buffer. Java SHOULD enforce
                 * this operation as being thread-safe. */
                Iterator<SendBuf> i = map_of_buffers.values().iterator();
                while( i.hasNext() ) {
                    i.next().putInBuf(to_send);
                }
            } catch (InterruptedException x ) {
                interrupt();
            }
        }
        System.out.println("Server arbiter thread exiting.");
    }
}
