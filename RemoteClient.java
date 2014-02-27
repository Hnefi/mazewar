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
 * A skeleton for those {@link Client}s that correspond to clients on other computers.
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: RemoteClient.java 342 2004-01-23 21:35:52Z geoffw $
 */

public class RemoteClient extends Client implements Runnable {

        private final ClientArbiter arbiter;        

        /**
         * Create a remotely controlled {@link Client}.
         * @param name Name of this {@link RemoteClient}.
         */
        public RemoteClient(String name, ClientArbiter arb) {
                super(name);
                arbiter = arb;
        }

        /**
         * May want to fill in code here.
         */ 

        public void run(){
            assert(arbiter != null);
            boolean keep_running = true;
            while(keep_running){
                keep_running = arbiter.requestServerAction(this);
            }   
        }
}
