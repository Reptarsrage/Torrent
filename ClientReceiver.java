/**
 * Justin Robb
 * 1024715
 * 12/13/13
 * cse461 Final Project
 * Handles downloading data from a peer
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;

/**
 * Handles a connection to one peer. 
 * Receives data from this peer and writes the data to a file.
 * 
 * @author Justin Robb
 *
 */
public class ClientReceiver extends Thread{
	private boolean shutdown_normally = false; 	// Should we show error messages? (false = yes)
    ByteBuffer buf;							   	// Used for both sending and receiving
	int id;										// id for client doing the downloading
	int peer_id;								// id for peer sending data
	String filepath;							// path of file to write data to
	int file_id;								// id of file to write data to
	Socket peer_connection;						// connection to peer
	public int start_index;						// index in file to write
	public int stop_index;						// index in file to stop writing
	public int bytes_written;					// number of bytes written to file by this thread
	
	/**
	 * Constructs a thread to handle downloading a chunk from a peer.
	 * 
	 * @param id Id for client doing the downloading.
	 * @param filepath Path of file to write data to.
	 * @param file_id Id of file to write data to.
	 * @param peer_id Id for peer sending data.
	 * @param peer Socket representing connection to peer.
	 */
	public ClientReceiver(int id, String filepath, int file_id, int peer_id, Socket peer) {
		this.id = id;
		this.file_id = file_id;
		this.filepath = filepath;
		this.peer_id = peer_id;
		this.peer_connection = peer;
	}
	
	/**
	 * Sets the interval of the chunk being received from peer & written to file.
	 * 
	 * @param start_index The index in the file to start writing the data.
	 * @param stop_index Start index + data length;
	 */
	public void setInterval(int start_index, int stop_index){
		this.start_index = start_index;
		this.stop_index = stop_index;
	}
	
	/**
	 * Should be called when the server is shut down by the client.
	 * This closes the socket and exits without errors.
	 */
	@Override
	public void interrupt() {
		shutdown_normally = true;
		try {
		if (!peer_connection.isClosed())
			peer_connection.close();
		} catch (IOException e) {
			return;
		}
	}
	

	/**
	 * Receives data from peer then writes data to file.
	 */
	@Override
	public void run() {
		//(for download req c->p) [header | file_id (4) | start index (4) | stop index (4)]
		//(for download chunk p->c) [header | file_id (4) | start index (4) | data (?)]
		try {
			System.out.println("Waiting for peer to send data @" + peer_connection.getInetAddress());
			if (stop_index - start_index == 0)
	    		throw new RuntimeException("BAD INTERVAL");
	    	peer_connection.setSoTimeout(Constants.PEER_TIMEOUT);
	    	OutputStream out = new BufferedOutputStream(peer_connection.getOutputStream());
			InputStream in = new BufferedInputStream(peer_connection.getInputStream());
	    	
			// send ack to peer
			buf = Utility.addHeader(Constants.PEER, 12, id);
	    	buf.putInt(Constants.HEADER_LEN, file_id);
	    	buf.putInt(Constants.HEADER_LEN + 4, start_index);
	    	buf.putInt(Constants.HEADER_LEN + 8, stop_index);
	    	out.write(buf.array());
	    	out.flush();
	    	
			while(!shutdown_normally) {
		    	if ((buf=Utility.readIn(in, Constants.HEADER_LEN)) == null){
		    		in.close();
		    		out.close();
		    		throw new IOException("read failed.");
		    	}
		    	if (buf.getInt(0) == Constants.CLOSE_CONNECTION && buf.getInt(8) != peer_id){
		    		shutdown_normally = true;
		    	} else if (buf.getInt(0) != Constants.PEER || buf.getInt(8) != peer_id) {
		    		in.close();
		    		out.close();
		    		throw new RuntimeException(
		    				"Malformed header from peer @" + peer_connection.getInetAddress());
		    	}
		    	int len = buf.getInt(4);
		    	// read payload
		    	if ((buf=Utility.readIn(in, Utility.getPaddedLength(len))) == null) {
		    		in.close();
		    		out.close();
		    		throw new IOException("read failed.");
		    	}
		    	if (buf.getInt(0) != file_id || buf.getInt(4) != start_index) {
		    		in.close();
		    		out.close();
		    		throw new RuntimeException(
		    				"Received a bad payload from peer @" + peer_connection.getInetAddress());
		    	}
		    	System.out.println("Received data from peer. start: "
		    		+start_index +", stop: "+stop_index +" @" + peer_connection.getInetAddress());
		    	byte[] data = new byte[len - 8];
		    	for (int i = 0; i < len - 8; i++){
		    		data[i] = buf.get(i + 8);
		    	}
		    	// aquire lock, write bytes to file
		    	bytes_written = FileWriter.getInstance().writeToFile(filepath, data, start_index);
		    	shutdown_normally = true; // never gets close request from server
			}
	    } catch (UnknownHostException e) {
			// IPAdress unknown
			if (!shutdown_normally)
			    System.err.println("Peer - Failure! @" + 
			peer_connection.getInetAddress() + " (" + e.getMessage() + ")");
		} catch (SocketException e) {
			// bad port
			if (!shutdown_normally)
				System.err.println("Peer - Failure! @" + 
			peer_connection.getInetAddress() + " (" + e.getMessage() + ")");
		} catch (IOException e) {
			// Error in communication
			if (!shutdown_normally)
				System.err.println("Peer - Failure! @" + 
			peer_connection.getInetAddress() + " (" + e.getMessage()+")");
		} catch (RuntimeException e){
			if (!shutdown_normally)
				System.err.println("Peer - Failure! @" + 
						peer_connection.getInetAddress() + " (" + e.getMessage()+")");
		} finally {
			System.out.println("Closing peer connection @" + peer_connection.getInetAddress());
			if (!peer_connection.isClosed())
				try {
					peer_connection.close();
				} catch (IOException e) {
					return;
				}
		}
	}
}
