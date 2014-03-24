import java.io.Serializable;
import java.util.*;
 /**
 * Game Packet that holds events and communication between game Server and various clients
 * ============
 * 
 */

public class GamePacket implements Serializable {
    /* Message types (can only really be moved/fired/died/spawned)
     *  - can add joined/left later on
     */
    public static final int CLIENT_NULL = -1;

    //Join/Leave Protocol Types
    public static final int RING_JOIN = 1;
    public static final int RING_LEAVE = 2;
    public static final int RING_REPLACE = 3;
    public static final int RING_SERVER_PORT = 4; 

    // these go in the event q
    public static final int CLIENT_MOVED_FORWARD = 101;
    public static final int CLIENT_MOVED_BACK = 102;
    public static final int CLIENT_INVERT = 103;
    public static final int CLIENT_TURN_L = 104;
    public static final int CLIENT_TURN_R = 105;
    public static final int CLIENT_FIRED = 300;
    public static final int CLIENT_KILLED = 404;
    public static final int CLIENT_SPAWNED = 42;
    public static final int CLIENT_JOINED = 202;
    public static final int CLIENT_LEFT = 203;
    public static final int CLIENT_REMOTE_LOC = 22;
    public static final int CLIENT_NOP = 666;

    // this is only ever sent out and never received
    public static final int SET_RAND_SEED = 999;

    // this is a type only used by the lookup server to reply to
    // new client joins
    public static final int ADDR_PORT_LIST = 400;

    // default message type
    public int type = GamePacket.CLIENT_NULL;
	
    // identifier for player
    public String player_name = null;

    // port this player is listening on
    public int port = -1;

    // identifier for the dead player
    public String john_doe = null;

    // rand seed
    public int seed = -1;

    // player score
    public int score = -1;

    // spawn point (NO FRIGGIN CAMPING)
    public DirectedPoint location = null;
    
    // this is a list of APP's that are used for joining the game
    public ArrayList<AddressPortPair> list_of_others = null;

    // this is the player's id number (only used upon first reply from lookup server)
    public int pid = -1;
}
