/** FileChangeNotifier.java
 *
 * watches a file and notifies when the file is changed. 
 * referred to 
 * http://www.rgagnon.com/javadetails/java-0214.html
 * 
 * Kota Miura (miura@embl)
 * http://cmci.embl.de
 * Nov 24, 2012
 */
package emblcmci.util;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Observable;

import javax.swing.Timer;

public class FileChangeNotifier  extends Observable implements ActionListener{
	Timer t = new Timer(1000,this); // check every second
	long lastModified;
	String filepath;

	FileChangeNotifier (String s) {
		filepath = s;
		File f = new File(filepath);
		lastModified = f.lastModified(); // original timestamp
		t.start();
	} 
	@Override
	public void actionPerformed(ActionEvent e) {
		File f = new File(filepath);
		long actualLastModified = f.lastModified() ;
		if (lastModified != actualLastModified) {
			// the file have changed
			lastModified = actualLastModified;
			setChanged();
			notifyObservers();
		}
	}
}
