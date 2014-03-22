/**
 * Justin Robb
 * 12/13/13
 * Peer to Peer connection
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

/**
 * Handles the task of connecting an unloader (peer) to a downloader (client).
 * and forwarding data for one file inbetween them.
 * 
 * @author Justin Robb
 *
 */
public class ServerFwding extends Thread {
	Socket peer_connection;				// connection to peer (uploader)
	ServerSocket peer_server;			// connection to receive a peer		
	Socket client_download_connection;	// connection to receive a client
	ServerSocket client_server;			// connection to peer (downloader)
	ByteBuffer buf;						// Used for both sending and receiving
	int client_server_port;				// Port to listen for client on
	int peer_id;						// id of peer
	int client_id;						// id of client
	int peer_port;						// port to listen for peer on
	int file_id;						// id of file being exchanged
	
	/**
	 * Constructs a peer to peer connection.
	 * 
	 * @param client_server_port The port to listen for the downloader on.
	 * @param id The id for the uploader.
	 * @param peer_port The port to listen for the uploader on.
	 * @param file_id The id of the file being exchanged.
	 */
	public ServerFwding(int client_server_port, int id, int peer_port, int file_id)  {
		this.client_server_port = client_server_port;
		this.peer_port = peer_port;
		this.peer_id = id;
		this.file_id = file_id;
	}
	
	private void closeConnection(InputStream in, OutputStream out, Socket connection, int id) throws IOException {
		buf = Utility.addHeader(Constants.CLOSE_CONNECTION, 0, id);
		out.write(buf.array());
		out.flush();
		out.close();
		in.close();
		connection.close();
		System.out.println(" (FWD) Closed connection.");
	}
	
	/**
	 * Forwards packets in between two peers.
	 */
	/* NOTE:
	 * Originally just had peers connect to themselves, but
	 * this proved to be a bad idea because of peers behind NAT
	 * did not want to connect to each other. The only possible solution
	 * was this, forward everything through the server. While I don't like
	 * this b/c the server shouldn't have to do this extra work, it was
	 * the only solution.
	 */
	@Override
	public void run() {
		try {
			// await peer connection on server
			peer_server = new ServerSocket(peer_port);
			client_server = new ServerSocket(client_server_port);
			peer_server.setSoTimeout(Constants.PEER_TIMEOUT);
			client_server.setSoTimeout(Constants.PEER_TIMEOUT);
			
			try {
				peer_connection = peer_server.accept();
			} catch(SocketTimeoutException e){
				// timeout, still wait for client and then close
				client_download_connection = client_server.accept();
				InputStream in_c = new BufferedInputStream(
						client_download_connection.getInputStream()); 
				OutputStream out_c = new BufferedOutputStream(
						client_download_connection.getOutputStream());
				closeConnection(in_c, out_c, client_download_connection, peer_id);
				throw e;
			}
			// receive ack from peer
			//(for connected ack (on port num) c->s) [header | file_id | peer id]
			OutputStream out_p = new BufferedOutputStream(peer_connection.getOutputStream());
			InputStream in_p = new BufferedInputStream(peer_connection.getInputStream());
			
			if ((buf = Utility.readIn(in_p, Constants.HEADER_LEN + 8)) == null){
				closeConnection(in_p, out_p, peer_connection, client_id);
				throw new IOException("read failed.");
			}

			// check correctness
			if (!Utility.checkHeader(buf, Constants.PREPARE, 8, peer_id)){
				closeConnection(in_p, out_p, peer_connection, client_id);
				throw new RuntimeException("Malformed header");
			}
			if (file_id != buf.getInt(Constants.HEADER_LEN)){
				closeConnection(in_p, out_p, peer_connection, client_id);
				throw new RuntimeException("ACK received for wrong file");
			}
			
			// await client connection on fwd_server
			try {
				client_download_connection = client_server.accept();
			} catch (SocketTimeoutException e){
				// timeout
				closeConnection(in_p, out_p, peer_connection, client_id);
				throw e;
			}
			InputStream in_c = new BufferedInputStream(
					client_download_connection.getInputStream()); 
			OutputStream out_c = new BufferedOutputStream(
					client_download_connection.getOutputStream());
			
			// client is connected, start fwding the packets between
			//(for download req c->p) [header | file_id (4) | start index (4) | stop index (4)]
			//(for download chunk p->c) [header | file_id (4) | start index (4) | data (?)]
			
			// fwd c->p download req
			System.out.println(" (FWD) Fwding request from " + client_id + 
					" to peer " + peer_id);
			if ((buf =Utility.readIn(in_c, Constants.HEADER_LEN + 12)) == null){
				closeConnection(in_p, out_p, peer_connection, client_id);
				closeConnection(in_c, out_c, client_download_connection, peer_id);
				throw new IOException("read failed");
			}
			client_id = buf.getInt(8);
			out_p.write(buf.array());
			out_p.flush();
			
			// fwd p->c data		
			System.out.println(" (FWD) Fwding request from " + peer_id + 
					" to client " + client_id);
			// header
			if ((buf =Utility.readIn(in_p, Constants.HEADER_LEN)) == null){
				closeConnection(in_p, out_p, peer_connection, client_id);
				closeConnection(in_c, out_c, client_download_connection, peer_id);
				throw new IOException("read failed");
			}
			out_c.write(buf.array());
			
			// and payload
			if ((buf =Utility.readIn(in_p, buf.getInt(4))) == null){
				closeConnection(in_p, out_p, peer_connection, client_id);
				closeConnection(in_c, out_c, client_download_connection, peer_id);
				throw new IOException("read failed");
			}
			out_c.write(buf.array());
			out_c.flush();
			
			// close connections
			// peer
			closeConnection(in_p, out_p, peer_connection, client_id);
			
			// client
			closeConnection(in_c, out_c, client_download_connection, peer_id);
		} catch (IOException e) {
			System.err.println(" (FWD) Peer connection failed: " + e.getMessage());
		} catch (RuntimeException e) {
			System.err.println(" (FWD) Peer connection failed: " + e.getMessage());
		} finally {
			try {
				if (!client_server.isClosed())	
					client_server.close();
				if (!peer_server.isClosed())
					peer_server.close();
			} catch (IOException e) {
				return;
			}
		}
	}
}
