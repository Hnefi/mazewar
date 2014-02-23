import java.net.*;
import java.io.*;
import java.util.concurrent.atomic.*;

/* This class is a worker that constantly blocks on the queue of events.
 * When it is woken up, it simply removes each of those events from the 
 * queue monotonically, tags them with a monotonically increasing int,
 * and spams them out to all the connected clients.
 */
public class ServerArbiter extends Thread {

}
