/*
Copyright (C) 2004 Geoffrey Alan Washburn
    
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.
    
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
    
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
USA.
*/
   
/**
 * {@link ClientEvent} encapsulates events corresponding to actions {@link Client}s may take.   
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: ClientEvent.java 359 2004-01-31 20:14:31Z geoffw $
 */

public class ClientEvent {
        /* Internals ******************************************************/
        
        /**
         * Internal representations of events.
         */

        //Gameplay events
        private static final int MOVE_FORWARD = 0;
        private static final int MOVE_BACKWARD = 1;
        private static final int TURN_LEFT = 2;
        private static final int TURN_RIGHT = 3;
        private static final int INVERT = 4;
        private static final int FIRE = 5;
        private static final int KILL = 6;
        private static final int SPAWN = 7;

        //Player join events
        private static final int JOIN = 8;
        private static final int LEAVE = 9;
        private static final int LOCATION_REQUEST = 10;
        private static final int LOCATION_RESPONSE = 11;
        private static final int REMOTE_LOCATION = 12;
        private static final int LOCATION_COMPLETE = 13;
        private static final int SET_RANDOM_SEED = 14;
        private static final int DIE = 15;
 
        /**
         * Default to 0, to be invalid.
         */
        private final int event;
        
        /**
         * Create a new {@link ClientEvent} from an internal representation.
         */
        private ClientEvent(int event) {
                assert((event >= 0) && (event <= 4));
                this.event = event;
        }

        /* Public data ****************************************************/
        
        /** 
         * Generated when a {@link Client} moves forward.
         */
        public static final ClientEvent moveForward = new ClientEvent(MOVE_FORWARD);

        /**
         * Generated when a {@link Client} moves backward.
         */
        public static final ClientEvent moveBackward = new ClientEvent(MOVE_BACKWARD);

        /**
         * Generated when a {@link Client} turns left.
         */
        public static final ClientEvent turnLeft = new ClientEvent(TURN_LEFT);

        /**
         * Generated when a {@link Client} turns right.
         */
        public static final ClientEvent turnRight = new ClientEvent(TURN_RIGHT);

        /**
         * Generated when a {@link Client} turns right.
         */
        public static final ClientEvent invert = new ClientEvent(INVERT);

       /**
         * Generated when a {@link Client} fires.
         */
        public static final ClientEvent fire = new ClientEvent(FIRE);
        
        /**
         * Generated when a {@link Client} spawns.
         */
        public static final ClientEvent kill = new ClientEvent(KILL);

        /**
         * Generated when a {@link Client} spawns.
         */
        public static final ClientEvent spawn = new ClientEvent(SPAWN);

        /**
         * Generated when a {@link Client} joins the session.
         */
        public static final ClientEvent join = new ClientEvent(JOIN);

        /**
         * Generated when a {@link Client} leaves the session.
         */
        public static final ClientEvent leave = new ClientEvent(LEAVE);

        /**
         * Generated when a {@link Client} is asked to report its location.
         */
        public static final ClientEvent locationRequest = new ClientEvent(LOCATION_REQUEST);
        
        /**
         * Generated when a {@link Client} reports its location.
         */
        public static final ClientEvent locationResponse = new ClientEvent(LOCATION_RESPONSE);
        
        /**
         * Generated when a {@link Client} receives the location of a {@link RemoteClient}.
         */
        public static final ClientEvent remoteLocation = new ClientEvent(REMOTE_LOCATION);

        /**
         * Generated when a {@link Client} has received the locations for all other clients.
         */
        public static final ClientEvent locationComplete = new ClientEvent(LOCATION_COMPLETE);

        /** 
         * Generated when a {@link Client} receives notice of the random seed.
         */
        public static final ClientEvent setRandomSeed = new ClientEvent(SET_RANDOM_SEED);

        /**
         *  Generated when a {@link Client} is about to die!
         */
        public static final ClientEvent die = new ClientEvent(DIE);
}
