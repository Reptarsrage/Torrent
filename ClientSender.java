/**
 * Justin Robb
 * 1024715
 * 12/13/13
 * cse461 Final Project
 * Handles uploading data to a peer
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;

/**
 * Handles connection to one peer. 
 * Reads data from a file and sends the data to peer.
 * 
 * @author Justin Robb
 *
 */
public class ClientSender extends Thread{
	private boolean shutdown_normally = false; 	// Should we show error messages? (false = yes)
    Socket peer_connection;						// connection to peer
	ByteBuffer buf;							   	// Used for both sending and receiving
	int id;										// id for client doing the downloading
	int peer_id;								// id for peer sending data
	String filepath;							// path of file to write data to
	int file_id;								// id of file to write data to
	
	/**
	 * Constructs a thread to handle uploading a chunk to a peer.
	 
	 * @param id Id for client doing the downloading.
	 * @param filepath Path of file to write data to.
	 * @param file_id Id of file to write data to.
	 * @param peer_id Id for peer sending data.
	 * @param addr Remote address of peer.
	 * @param port Remote port of peer.
	 * @throws IOException
	 */
	public ClientSender(int id, String filepath, int file_id, 
			int peer_id, InetAddress addr, int port) throws IOException{
		peer_connection = new Socket(addr, port);
		this.id = id;
		this.file_id = file_id;
		this.filepath = filepath;
		this.peer_id = peer_id;
	}
	
	/**
	 * Should be called when the server is shut down by the client.
	 * This closes the socket and exits without errors.
	 */
	@Override
	public void interrupt() {
		shutdown_normally = true;
		try {
			peer_connection.close();
		} catch (IOException e) {
			return;
		}
	}
	
	/**
	 * Reads in data from a file and sends the data to a peer.
	 * 
	 * @param in Stream to read data from peer.
	 * @param out Stream to write data to peer.
	 * @param buf Buffer containing payload for peer request.
	 * @throws IOException
	 * @throws RuntimeException
	 */
	private void readAndSend(InputStream in, OutputStream out, 
			ByteBuffer buf) throws IOException, RuntimeException{
		//(for download req c->p) [header | file_id (4) | start index (4) | stop index (4)]
		//(for download chunk p->c) [header | file_id (4) | start index (4) | data (?)]
		
		int buf_file_id = buf.getInt(0);
		int start_index = buf.getInt(4);
		int stop_index = buf.getInt(8);
		if (buf_file_id != file_id){
			in.close();
			out.close();
			throw new RuntimeException("Peer requested wrong file id.");
		}
		int len = stop_index - start_index;
		
		//read file
		System.out.println("Reading from file from " + start_index +" to "+ stop_index);
		FileInputStream file_in = new FileInputStream(filepath);
		if (start_index != file_in.skip(start_index)){
			in.close();
			out.close();
			file_in.close();
			throw new IOException("Error reading file " + filepath);
		}
		if ((buf=Utility.readIn(file_in, len)) == null){
			in.close();
			out.close();
			file_in.close();
			throw new IOException("read failed.");
		}
		byte[] fileData = buf.array();
		
		//construct response
		System.out.print("Sending peer data from file @" + peer_connection.getInetAddress() +"...");
		buf = Utility.addHeader(Constants.PEER, len + 8, id); 
		buf.putInt(Constants.HEADER_LEN, file_id);
		buf.putInt(Constants.HEADER_LEN + 4, start_index);
		for (int i = 0; i < fileData.length; i++){
			buf.put(Constants.HEADER_LEN +8+ i, fileData[i]);
		}
		out.write(buf.array());
		out.flush();
		System.out.print("done!\n");
	}
	
	/**
	 * Receives req from peer for a chunk of a file, reads the chunk and sends it to peer.
	 */
	@Override
	public void run() {
		try {
			peer_connection.setSoTimeout(Constants.PEER_TIMEOUT);
			InputStream in = new BufferedInputStream(peer_connection.getInputStream()); 
			OutputStream out = new BufferedOutputStream(peer_connection.getOutputStream());
	    	
			// send ack to peer
			//(for connected ack (on port num) c->s) [header | file_id | peer id]	
			buf = Utility.addHeader(Constants.PREPARE, 8, id);
			buf.putInt(Constants.HEADER_LEN, file_id);
			buf.putInt(Constants.HEADER_LEN + 4, peer_id);
			out.write(buf.array());
			out.flush();
			while(!shutdown_normally){  
				//(for download req c->p) [header | file_id (4) | start index (4) | stop index (4)]
				//(for download chunk p->c) [header | file_id (4) | start index (4) | data (?)]
				// WE ARE P
				// only if there's something to read, otherwise idle
				System.out.println("Awaiting request from peer @" + peer_connection.getInetAddress() + "...");
				if ((buf=Utility.readIn(in, Constants.HEADER_LEN)) == null) {
					out.close();
					in.close();
					throw new IOException("read failed.");
				}
				
				System.out.println("Request from peer @" + peer_connection.getInetAddress() + "..Received!");
				// two cases, either close or send
				int flag = buf.getInt(0);
				if (flag == Constants.CLOSE_CONNECTION){
					if (!Utility.checkHeader(buf, Constants.CLOSE_CONNECTION, 0, peer_id)){
						in.close();
						out.close();
						throw new RuntimeException("Malformed header from peer @" + 
								peer_connection.getInetAddress());
					}
					shutdown_normally = true;
					break;
				} else if (flag == Constants.PEER){
					System.out.println("Processing peer request...");
					if (buf.getInt(8) != peer_id){
						in.close();
						out.close();
						throw new RuntimeException("Malformed header from peer @" + 
								peer_connection.getInetAddress());
					}
					if ((buf = Utility.readIn(in, 12)) == null) {
						out.close();
						in.close();
						throw new IOException("read failed.");
					}
					readAndSend(in, out, buf);
				} else
					throw new RuntimeException("Malformed header from peer @" + 
							peer_connection.getInetAddress());
			}
			out.close();
			in.close();
	    } catch (UnknownHostException e) {
			// IPAdress unknown
			if (!shutdown_normally)
			    System.err.println("Peer - Failure! @" + peer_connection.getInetAddress() + 
			    		" (" + e.getMessage() + ")");
		} catch (SocketException e) {
			// bad port
			if (!shutdown_normally)
				System.err.println("Peer - Failure! @" + peer_connection.getInetAddress() + 
						" (" + e.getMessage() + ")");
		} catch (IOException e) {
			// Error in communication
			if (!shutdown_normally)
				System.err.println("Peer - Failure! @" + peer_connection.getInetAddress() + " (" +
						e.getMessage()+")");
		} catch (RuntimeException e) {
			// Error in communication
			if (!shutdown_normally)
				System.err.println("Peer - Failure! @" + peer_connection.getInetAddress() + " (" +
						e.getMessage()+")");
		} finally {
			System.out.println("Closing peer connection @" + peer_connection.getInetAddress());
			try {
				if (!peer_connection.isClosed())
					peer_connection.close();
			} catch (IOException e) {
				return;
			}
		}
	}
}
