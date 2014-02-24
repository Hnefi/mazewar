import java.io.Serializable;
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

    // default message type
	public int type = GamePacket.CLIENT_NULL;
	
    // identifier for player
	public String player_name;

    // identifier for the dead player
    public String john_doe = null;

    // packet direction
    public boolean request;

    // parent timestamp
    public int tstamp = -1;

    // spawn point (NO FRIGGIN CAMPING)
    public Point you_are_here = null;
    public Direction i_want_it_that_way = null;

}
