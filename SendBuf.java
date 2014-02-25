import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/* This class belongs to a GameServerSenderThread, and simply has a concurrent buffer that events
 * are written into. The sender thread then takes things OUT of this buffer and puts them on socks */

public class SendBuf {
    private final ArrayBlockingQueue<GamePacket> buf;

    public SendBuf(int capacity)
    {
        /* Create a blocking queue of this size. */
        buf = new ArrayBlockingQueue<GamePacket>(capacity);
    }

    public GamePacket takeFromBuf() {
        GamePacket to_ret = null;
        try {
            to_ret = buf.take(); // blocking
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt(); // propagate
        }
        return to_ret;
    }

    public void putInBuf(GamePacket to_buf) {
        try {
            buf.put(to_buf); // blocking
        } catch (InterruptedException x) {
            Thread.currentThread().interrupt(); // propagate
        }
    }
}
