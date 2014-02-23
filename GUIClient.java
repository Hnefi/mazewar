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

import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.HashMap;

class actionKey {

    private Direction d;
    private int keyCode;

    public actionKey(int keyCodeIn, Direction dIn){
        this.d = dIn;
        this.keyCode = keyCodeIn;
    }

    //@Override
    public boolean equals(Object o){
        if (this == o) return true;
        if (!(o instanceof actionKey)) return false;
        actionKey k = (actionKey) o;
        return (d.equals(k.d)) && (k.keyCode == this.keyCode);
    }

    //@Override
    public int hashCode(){
        return (d.toString().hashCode())*4 + keyCode;
    }
}
/**
 * An implementation of {@link LocalClient} that is controlled by the keyboard
 * of the computer on which the game is being run.  
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: GUIClient.java 343 2004-01-24 03:43:45Z geoffw $
 */

public class GUIClient extends LocalClient implements KeyListener {


        private Map actionByPressAndDirMap;

        private static final int FORWARD = 0;
        private static final int INVERT = 1;
        private static final int LEFT = 2;
        private static final int RIGHT = 3;

        /**
         * Create a GUI controlled {@link LocalClient}.  
         */
        public GUIClient(String name) {
                super(name);
                actionByPressAndDirMap = new HashMap();
                
                //Map for determining move based on direction and keypress
                //Makefile restricts us to java 1.4 and hence no generics; hence all the ugly casting!
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_UP, Direction.North), (Object) new Integer(FORWARD));
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_UP, Direction.South), (Object) new Integer(INVERT));
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_UP, Direction.East), (Object) new Integer(LEFT));
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_UP, Direction.West), (Object) new Integer(RIGHT));
                
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_DOWN, Direction.North), (Object) new Integer(INVERT));
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_DOWN, Direction.South), (Object) new Integer(FORWARD));
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_DOWN, Direction.East), (Object) new Integer(RIGHT));
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_DOWN, Direction.West), (Object) new Integer(LEFT));

                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_LEFT, Direction.North), (Object) new Integer(LEFT));
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_LEFT, Direction.South), (Object) new Integer(RIGHT));
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_LEFT, Direction.East), (Object) new Integer(INVERT));
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_LEFT, Direction.West), (Object) new Integer(FORWARD));

                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_RIGHT, Direction.North), (Object) new Integer(RIGHT));
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_RIGHT, Direction.South), (Object) new Integer(LEFT));
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_RIGHT, Direction.East), (Object) new Integer(FORWARD));
                actionByPressAndDirMap.put((Object) new actionKey(KeyEvent.VK_RIGHT, Direction.West), (Object) new Integer(INVERT));
        }
        
        /**
         * Handle a key press.
         * @param e The {@link KeyEvent} that occurred.
         */
        public void keyPressed(KeyEvent e) {
                Direction d = getOrientation();
                char keyPress = e.getKeyChar();
                int keyCode = e.getKeyCode();
                int action = -1;
                try {
                    //find your action code
                    action = ((Integer) actionByPressAndDirMap.get((Object) new actionKey(keyCode, d))).intValue();
                } catch (NullPointerException nullE){
                    //keep action as -1; we register fires by the keyCode
                };
                // If the user pressed Q, invoke the cleanup code and quit. 
                if((keyPress == 'q') || (keyPress == 'Q')) {
                        Mazewar.quit();
                // Up-arrow moves forward.
                } else if(action == FORWARD) {
                        forward();
                // Down-arrow moves backward.
                } else if(action == INVERT) {
                        invert();
                // Left-arrow turns left.
                } else if(action == LEFT) {
                        turnLeft();
                // Right-arrow turns right.
                } else if(action == RIGHT) {
                        turnRight();
                // Spacebar fires.
                } else if(keyCode == KeyEvent.VK_SPACE) {
                        fire();
                }
        }
        
        /**
         * Handle a key release. Not needed by {@link GUIClient}.
         * @param e The {@link KeyEvent} that occurred.
         */
        public void keyReleased(KeyEvent e) {
        }
        
        /**
         * Handle a key being typed. Not needed by {@link GUIClient}.
         * @param e The {@link KeyEvent} that occurred.
         */
        public void keyTyped(KeyEvent e) {
        }

}
