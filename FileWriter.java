/**
 * Justin Robb
 * 12/13/13
 * Write data to a file
 */

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Singleton class to write data to a file.
 * 
 * @author Justin Ronn
 *
 */
public class FileWriter {
    /**Ensures singleton property**/
	private static final FileWriter inst= new FileWriter();
	
	/**Ensures singleton property**/
    private FileWriter() {
        super();
    }

    /**
     * Writes data to a file at the given index in the file.
     * 
     * @param path The path to the file to be written.
     * @param data The data to write in the file.
     * @param start The index within the file to write the data.
     * @return The number of bytes written.
     */
    public synchronized int writeToFile(String path, byte[] data, int start){
        int bytes_written = 0;
    	try {
    		File dest = new File(path);
    		if (!dest.exists())
    			dest.createNewFile();
        	RandomAccessFile file = new RandomAccessFile(path, "rw");
        	file.seek(start);
        	file.write(data);
        	bytes_written = data.length;
        	file.close();
        	System.out.println("Write Succeeded.");
        	return bytes_written;
        } catch (IOException e){
        	return bytes_written;
        }
    }
    /**
     * Gets the singleton file writer for use.
     * 
     * @return An instance of this class.
     */
    public static FileWriter getInstance() {
        return inst;
    }
}
