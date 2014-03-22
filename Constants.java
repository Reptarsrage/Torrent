/**
 * Justin Robb
 * 1024715
 * 12/13/13
 * cse461 Final Project
 * Constant values
 */

/**
 * All of the constant values used by server and clients.
 * 
 * @author Justin Robb
 *
 */
public interface Constants {
	
	// VALUES
	final static int DEFAULT_PORT = 12235;		// Default port to run a server on
	final static int PEER_TIMEOUT = 5000; 		// secs timeout when peers communicate
	final static int PAGE_SIZE = 10;			// size of a page of torrent files
	final static int HEADER_LEN = 12; 			// Length of the protocol Header field
	final static String GREETING = "JUSTIN\0"; 	// Each request for a connection must contain this string
	static final int ACK_TIMEOUT = 10000;		// how long to wait before giving up on client
	
	
	// PROTOCOL FLAGS
	final static int NUM_FLAGS = 8;			// Number of protocol flags
	final static int PEER = 8;				// peer to peer connection packet
	final static int PREPARE = 7;			// prepare peer for a peer to peer connection
	final int ACK = 6;						// Acknowledgment of being alive
	final static int CLOSE_CONNECTION = 5;	// Request to close a connection
	final static int DOWNLOAD_ACK = 4;		// Acknowledgment of download completion
	final static int UPLOAD_REQ = 3;		// Request for uploading
	final static int DOWNLOAD_REQ = 2;		// Request for downloading
	final static int VIEW_REQ = 1;			// request to retrieve files
	final static int OPEN_CONNECTION = 0;	// request to open connection
}
