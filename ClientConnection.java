/**
 * Justin Robb
 * 12/13/13
 * Client Listener
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Listens for requests from the server to set up peer connections. 
 * 
 * @author Justin Robb
 *
 */
public class ClientConnection extends Thread{
	private boolean shutdown_normally = false;	// Should we show error messages? (false = yes)			
	List<ClientSender> threadpool;				// List of managed peer connections
    Socket server_connection;					// Connection to the server
    ByteBuffer buf;								// Used for both sending and receiving
	int id;										// Id of the client (us)
	private Map<Integer, String> uploaded_files;// List of uploaded files (by the client [us])
												// Thread safe
    
	/**
	 * Sets our client id to the given id.
	 * 
	 * @param id The client id for this listener.
	 */
	public void setId(int id){
		this.id = id;
	}
	
	/**
	 * Constructs a listener. 
	 * IMPORTANT: Still needs id and connection to run.
	 * 
	 * @param uploaded_files The files uploaded by this client.
	 */
	public ClientConnection(Map<Integer, String> uploaded_files) {
		threadpool = new ArrayList<ClientSender>();
		id = 0;
		this.uploaded_files = uploaded_files;
	}
	
	/**
	 * Sets up a connection to the server used by this listener.
	 * 
	 * @param addr The address of the Server.
	 * @param port The port to connect to.
	 * @throws IOException
	 */
	public void setupConnection(InetAddress addr, int port) throws IOException {
		server_connection = new Socket(addr, port);
	}
	
	/**
	 * Should be called when the server is shut down by the client.
	 * This closes the socket and exits, bypassing the infinite loop 
	 * of listening for requests. Ends all managed threads.
	 */
	@Override
	public void interrupt() {
		shutdown_normally = true;
		for (ClientSender c : threadpool){
			c.interrupt();
		}
		try {
			// force accept to shut down
			server_connection.close();
		} catch (IOException e) {
			return;
		}
	}
	
	/**
	 * Must have an id and connection before running.
	 */
	@Override
	public void run() {
		try {   
			server_connection.setSoTimeout(0);
			OutputStream out = new BufferedOutputStream(server_connection.getOutputStream());
			InputStream in = new BufferedInputStream(server_connection.getInputStream());
			while(!shutdown_normally){  
		    	// must be from server with flag PREPARE or CLOSE_CONNECTION
				//(for listen for a client conn s->c) [header | file_id (4) | peer id]
				//(for listen ack c->s) [header | file_id | port_number]
				if ((buf = Utility.readIn(in, Constants.HEADER_LEN)) == null) {
					continue;
				}
				if (buf.getInt(0) == Constants.CLOSE_CONNECTION && buf.getInt(8) == id){
					// close it up
					break;
				}
				if (buf.getInt(0) == Constants.PREPARE && buf.getInt(8) == id){
					// read in payload
					//(for connect to a peer conn s->c) [header | file_id (4) | peer id (4) | port num]
					//(for connected ack (on port num) c->s) [header | file_id | port_number]
					if ((buf = Utility.readIn(in, 12)) == null) {
						out.close();
						in.close();
						throw new IOException("read failed.");
					}
					int file_id = buf.getInt(0);
					int peer_id = buf.getInt(4);
					int server_port = buf.getInt(8);

					// spawn new read/send thread
					String filepath = uploaded_files.get(file_id);
					if (filepath == null){
						in.close();
						out.close();
						throw new RuntimeException("File not available for peer connection.");
					}
					ClientSender c1 = new ClientSender(
							id, filepath, file_id, peer_id, server_connection.getInetAddress(), server_port);
					threadpool.add(c1);
					c1.start();
					System.out.println("Opening upload connection for file with id " + 
							file_id + " on port " + server_port);
				}	
			}
			in.close();
			out.close();
	    } catch (Exception e) {
			// IPAdress unknown
			if (!shutdown_normally)
			    System.err.println("Listener - Failure! (" + e.getMessage() + ")");
			throw new RuntimeException(e.getMessage()); // shut down client!
		} finally {
			try{
				if (server_connection != null)
					server_connection.close();
			} catch (IOException e){
				return;
			}
		}
	}
}
