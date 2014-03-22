/**
 * Justin Robb
 * 12/13/13
 * Utility functions
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/**
 * Contains common methods shared by server and client.
 * 
 * @author Justin Robb
 *
 */
public class Utility {
	static Random r = new Random();		// Random port generator
	
	/**
	 * Reads in a specified number of bytes from the given input stream.
	 * 
	 * @param in The input stream to read from.
	 * @param len The length of bytes to read.
	 * @return A buffer containing the bytes read, or null if failed to read all the bytes.
	 */
	public static ByteBuffer readIn(InputStream in, int len){
		try{
			ByteBuffer buf = ByteBuffer.allocate(len);
			byte[] receiveData = new byte[len];
			int read;
			int left_to_read = len;
			while (left_to_read > 0 && (read = in.read(receiveData, len - left_to_read, left_to_read)) != -1)
				left_to_read -= read;
			if (left_to_read != 0)
				return null;
			buf.put(receiveData);
			return buf;
		} catch (IOException e){
			return null;
		}
	}
	
	/**
	 * Gets a valid (unused) port.
	 * 
	 * @return The port number for a valid port.
	 */
	public static int getValidPort() {
		int thread_port;
		while (true){
			thread_port = r.nextInt(20000) + 40000;
	        try{
	        	ServerSocket try_socket = new ServerSocket(thread_port);
	        	try_socket.close();
	        	return thread_port;
	        } catch (IOException e) {
	        	// probably ran into a used port, so rechoose and run again!
	        	continue;
	        }
        }
	}
	
	/**
	 * Make sure the packet header follows protocol.
	 * 
	 * @param packet The buffered packet with a header to check.
	 * @param flag The protocol header flag (See Constants).
	 * @param len The length of the packet payload.
	 * @param id The unique identifier for the packet's client.
	 * @return True if header follows protocol.
	 */
	public static boolean checkHeader(ByteBuffer packet, int flag, int len, int id){
		// (header s->c or c->s) [action flag | payload len | client id] 
		// (client id is 0 if client is sening connect request)
		
		// parse packet
		int buf_flag = packet.getInt(0);
		int buf_len = packet.getInt(4);
		int buf_id = packet.getInt(8);

		// check fields for correctness	
		if (	buf_len != len ||
				buf_flag > Constants.NUM_FLAGS || 
				buf_flag < 0 || 
				buf_len < 0 ||
				buf_id != id) {
			return false;
		}
		
		// check length field for correctness
		if ((packet.capacity() - Constants.HEADER_LEN) != getPaddedLength(buf_len))
			return false;
		
		return true;	
	}
	
	/**
	 * Adds a valid header to the packet, pads the payload.
	 * 
	 * @param flag The protocol header flag (See Constants).
	 * @param len The length of the packet payload.
	 * @param id The unique identifier for the packet's client.
	 * @return A buffer containing the valid packet with an empty payload.
	 */
	public static ByteBuffer addHeader(int flag, int len, int id){
		// (header s->c or c->s) [action flag | payload len | client id] 
		ByteBuffer b = ByteBuffer.allocate(getPaddedLength(len) + Constants.HEADER_LEN);
		b.order(ByteOrder.BIG_ENDIAN);
		b.putInt(0, flag);
		b.putInt(4, len);
		b.putInt(8, id);
		return b;
	}
	
	/**
	 * Pads the payload length to be divisible by four. 
	 * 
	 * @param len Payload Length..
	 * @return the padded payload length.
	 */
	public static int getPaddedLength(int len){
		if (len % 4 != 0)
			len += (4 - (len % 4));
		return len;
	}
}
