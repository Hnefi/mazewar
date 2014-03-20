import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/* This class is the token that everyone sends around. It consists of a client event buffer that has the
 * proper number of slots for the number of connected clients (including robots), keeps track of
 * the number of connected machines, and has a Locations buffer for clients to use when a new join
 * is happening.
 */
public class Token {
    private ArrayDeque<GamePacket> new_events;
    private ArrayDeque<GamePacket> existing_locs;
    public boolean player_is_joining = false;
    public int num_machines = 0;
    public int num_players = 0; // this is different than num_machines because of robots

    /* Default constructor */ 
    public Token() {
        this.new_events = new ArrayDeque<GamePacket>();
        this.existing_locs = new ArrayDeque<GamePacket>();
    }

    // push and pop methods for queues
    public GamePacket takeFromQ () {
        if(player_is_joining) {
            return existing_locs.poll();
        }
        return new_events.poll();
    }

    public void putInQ(GamePacket p) {
        if(player_is_joining) {
            existing_locs.add(p);
            return;
        }
        new_events.add(p);
    }

    /* Setter method for re-initializing the events queue to the passed in
     * data structure.
     */
    public void overWriteEventQueue(AbstractCollection<GamePacket>new_q) {
        ArrayDeque<GamePacket> overwrite_with_this = new ArrayDeque<GamePacket>(new_q);
        this.new_events = overwrite_with_this; //garbage collect the old queue
    }
}

