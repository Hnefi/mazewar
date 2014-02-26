import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/* This thread is responsible for working with the synchronous join_q
 * object. It is sleeping most of the time and basically only will 
 * handle the cases where new clients are joining or leaving.
 */

public class ServerJoinDropThread extends Thread {
    // controls which queue that the recvr threads put events in
    private ArrayBlockingQueue<GamePacket> join_queue;
    private ConcurrentHashMap<String,SendBuf>map_of_buffers;
    private final AtomicInteger synch_point;

    public ServerJoinDropThread(
            ArrayBlockingQueue<GamePacket> joinQ,
            ConcurrentHashMap<String,SendBuf>bufMap,
            AtomicInteger cdl)
    {
        super("ServerJoinDropThread");
        this.join_queue = joinQ;
        this.map_of_buffers = bufMap;
        this.synch_point = cdl;
    }

    @Override
    public void run()
    {
        /* Basic thread implementation is as follows:
         *  Block on the join queue until we see a CLIENT_JOINED packet.
         *  This indicates that a new client has joined, and we need to
         *  do some dark magic thread sleeping/waking to ensure that this
         *  action is handled properly.
         *  1. Sleep the ServerArbiter thread. We don't want any more
         *  events being processed from the event queue until this join
         *  has been handled and completed.
         *  3. Send this join packet to all of the existing clients, and
         *  request that they send their locations in a new GamePacket.
         *  4. We know how many responses we are waiting for (because
         *  we know the number of sender buffers that exist and those 
         *  are 1 per connected player), so wait for that number of
         *  locations to come out of the join_queue. Whenever we get a 
         *  location packet, send it to the new player that joined!!
         *  After all the locations are sent to the new player, give him
         *  a packet that says all the locations are completed.
         *  5. The new client responds with a location request packet
         *  that tells the server where he is spawning. Send THAT out to
         *  all the current players.
         *  6. After sending that, send the new player a packet that
         *  tells him to start the game, and re-wake the ServerArbiter
         *  thread.
         */

        while ( isInterrupted() == false ) {
            /* As long as the synch point is zero, we sleep on it. The
             * Arbiter thread will wake us up when it's time. */
            synchronized (synch_point) {
                while ( synch_point.get() == 0 ) {
                    try {
                        synch_point.wait();
                    } catch (InterruptedException x) {
                        interrupt();
                    }
                }
            }

            System.out.println("Join/drop thread woken up by Arbiter!");
            String new_player_name = null;
            GamePacket p = null;
            try{
                p = join_queue.take();
                assert(p.type == GamePacket.CLIENT_JOINED); // first time
                new_player_name = p.player_name;
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
            }

            /* Extract some info from the packet and go to work. */
            System.out.println("New player = " + new_player_name);

            /* Step 1 - get location from all existing clients, and
             * the number of responses that I will need. Also forward
             * this JOIN to all of the connected clients, which will
             * tell them to make a new local thread.
             */
            GamePacket make_loc_thread = new GamePacket();
            make_loc_thread.player_name = new_player_name;
            make_loc_thread.type = GamePacket.CLIENT_JOINED;
            make_loc_thread.request = true;
            sendToAll(make_loc_thread);

            int num_players = map_of_buffers.size();
            System.out.println("Map of buffers was of size: " + num_players);

            /* Track the number of responses and send these packets
             * to the new client */
            int num_resp = 0;
            while (num_resp < num_players - 1) {
                try{
                    System.out.println("Join handler is waiting for " + (num_players - 1 - num_resp) + " more replies.");
                    GamePacket response = join_queue.take(); //block
                    assert(response.type == GamePacket.LOCATION_RESP);
                    response.type = GamePacket.REMOTE_LOC; 
                    sendToNewClient(new_player_name,response);
                    num_resp++;
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
            }

            /* Now send a new packet to the new client saying that
             * he's now gotten all of the remote locations. */
            System.out.println("Sending all loc done packet to new client.");
            GamePacket all_loc_comp = new GamePacket();
            all_loc_comp.type = GamePacket.ALL_LOC_DONE;
            all_loc_comp.player_name = new_player_name;
            sendToNewClient(new_player_name,all_loc_comp);

            /* Final step - the new client should give us a packet
             * that indicates the location he is to spawn at. */
            try {
                System.out.println("Getting location from the new guy.");
                GamePacket new_player_loc = join_queue.take(); //block
                assert(new_player_loc.type == GamePacket.LOCATION_REQ
                        && new_player_loc.player_name == new_player_name);
                new_player_loc.type = GamePacket.CLIENT_SPAWNED;
                new_player_loc.player_name = new_player_name;
                sendToAll(new_player_loc);
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
            }

            /* Now we are finished adding, so we can tell the 
             * ServerArbiter thread to wake up and go to sleep until
             * the next player joins and we have more to do. */

            /* Hand back to ServerArbiter */
            System.out.println("Handing back to the root ServerArbiter");
            synch_point.set(0);
            synch_point.notify();
            continue;
        }
    }

    /* Function that sends this game packet to all of the existing
     * players. Just simplifies the iterator code so it's not repeated.
     */
    private void sendToAll(GamePacket p)
    {
        Iterator<SendBuf> i = map_of_buffers.values().iterator();
        while( i.hasNext() ) {
            i.next().putInBuf(p);
        }
    }

    /* Function that sends this game packet to ONLY the new connected
     * player.
     */
    private void sendToNewClient(String new_client, GamePacket p)
    {
        // lookup by hashcode of new client
        SendBuf new_client_buf = map_of_buffers.get(new_client);
        new_client_buf.putInBuf(p);
    }
}
