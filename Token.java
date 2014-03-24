import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/* This class is the token that everyone sends around. It consists of a client event buffer that has the
 * proper number of slots for the number of connected clients (including robots), keeps track of
 * the number of connected machines, and has a Locations buffer for clients to use when a new join
 * is happening.
 */
public class Token implements Serializable {
    private ArrayDeque<GamePacket> eventQ;
    public int num_machines = 0;
    public int num_players = 0; // this is different than num_machines because of robots
    public AddressPortPair predecessorReplaceLoc = null;
    public AddressPortPair successorReplaceLoc = null;

    /* Default constructor */ 
    public Token() {
        this.eventQ = new ArrayDeque<GamePacket>();
    }

    // push and pop methods for queues
    public GamePacket takeFromQ () {
        return eventQ.poll();
    }

    public void putInQ(GamePacket p) {
        eventQ.add(p);
    }

    /* Setter method for re-initializing the events queue to the passed in
     * data structure.
     */
    public void overWriteEventQueue(AbstractCollection<GamePacket>new_q) {
        ArrayDeque<GamePacket> overwrite_with_this = new ArrayDeque<GamePacket>(new_q);
        this.eventQ = overwrite_with_this; //garbage collect the old queue
    }
}

