import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.Macro_Runner;
import ij.plugin.frame.*;
import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;

/** This plug-in creates a frame that accepts drag and drop
* and calls the selected macro for each file.
* based on the default sample plugin frame and the builtin DragAndDrop.java plugin
* Jerome Mutterer and Wayne Rasband.
*/

@SuppressWarnings("serial")
public class Drop_Javascript extends PlugInFrame implements DropTargetListener, Runnable, ActionListener {
	
	private Iterator iterator;
	Label l = new Label();
	Choice c = new Choice();
	//Choice exts = new Choice();
	Choice paths = new Choice();
	Button b = new Button();
	Button clear = new Button();
	Button code = new Button();
	//TODO consider changing this to the original location, or just omit saving such
	String defaultScriptsPath = IJ.getDirectory("imagej")+File.separator+"scripts"+File.separator;
	private boolean isNotDroppedYet = true; 

	public Drop_Javascript() {
		super("DropJavascript");
		if (IJ.versionLessThan("1.43i")) return;
		l.setText("Drop Javascripts here");
		File f = new File(defaultScriptsPath);
		if (!f.exists()){ 
			f.mkdir();
		}
		IJ.debugMode = true;		
		String[] list = f.list();	// a list of pre-existing macros
		if (countJSfiles(list)==0) {
			phantomJSGenerator();
			list = f.list();
		}
		for (int i=0; i<list.length; i++) {
			if (list[i].endsWith(".js")){
				paths.addItem(defaultScriptsPath + list[i]);	
				c.addItem(list[i]);
				if (IJ.debugMode) IJ.log(list[i]);
			}
		}
		b.setLabel("Run");
		b.addActionListener(this);
		clear.setLabel("Clear");
		clear.addActionListener(this);	
		code.setLabel("Code");
		code.addActionListener(this);			
		setLayout (new FlowLayout ());
		add(l);	add(c); add(b); add(clear);add(code);
		pack();
		GUI.center(this);
		new DropTarget(this, this);
		WindowManager.addWindow(this);
		setVisible(true); // depreciated show() sends a warning
	}

	public void phantomJSGenerator(){
		IJ.log("creating a js file");
		String exampleJS="IJ.log('hello world');" ;
		try {
		    BufferedWriter out = new BufferedWriter(new FileWriter(defaultScriptsPath+"helloworld.js"));
		    out.write(exampleJS);
		    out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public int countJSfiles(String[] list){
		int count = 0;
		for (int i = 0; i< list.length; i++){
			if (list[i].endsWith(".js")) count++;
		}
		return count;
	}
	// Droptarget Listener methods
	// behavior dependent on the file type, image file or a text file. 
	public void drop(DropTargetDropEvent dtde)  {
		dtde.acceptDrop(DnDConstants.ACTION_COPY);
		DataFlavor[] flavors = null;
		try  {
			Transferable t = dtde.getTransferable();
			iterator = null;
			flavors = t.getTransferDataFlavors();
			if (IJ.debugMode) IJ.log("Droplet.drop: "+flavors.length+" flavors");
			for (int i=0; i<flavors.length; i++) {
				if (IJ.debugMode) IJ.log("  flavor["+i+"]: "+flavors[i].getMimeType());
				if (flavors[i].isFlavorJavaFileListType()) {
					Object data = t.getTransferData(DataFlavor.javaFileListFlavor);
					iterator = ((java.util.List)data).iterator();
					if (IJ.debugMode) IJ.log("isFlavorJavaFileListType()");
					break;
				} else if (flavors[i].isFlavorTextType()) {
					Object ob = t.getTransferData(flavors[i]);
					if (!(ob instanceof String)) continue;	

					String s = ob.toString().trim();
					if (IJ.isLinux() && s.length()>1 && (int)s.charAt(1)==0)
						s = fixLinuxString(s);
					ArrayList list = new ArrayList();
					if (s.indexOf("href=\"")!=-1 || s.indexOf("src=\"")!=-1) {
						s = parseHTML(s);
						if (IJ.debugMode) IJ.log("  url: "+s);
						list.add(s);
						this.iterator = list.iterator();
						break;
					}
					BufferedReader br = new BufferedReader(new StringReader(s));
					String tmp;
					while (null != (tmp = br.readLine())) {
						tmp = java.net.URLDecoder.decode(tmp, "UTF-8");
						if (tmp.startsWith("file://")) tmp = tmp.substring(7);
						//if (IJ.debugMode) IJ.log("  content: "+tmp);
						IJ.log("  content: "+tmp);
						if (tmp.startsWith("http://"))
							list.add(s);
						else
							list.add(new File(tmp));
					}
					this.iterator = list.iterator();
					break;
				}
			}
			if (iterator!=null) {
				Thread thread = new Thread(this, "Drop_Javascript");
				thread.setPriority(Math.max(thread.getPriority()-1, Thread.MIN_PRIORITY));
				thread.start();
			}
		}
		catch(Exception e)  {
			dtde.dropComplete(false);
			return;
		}
		dtde.dropComplete(true);
		if (flavors==null || flavors.length==0)
			IJ.error("First drag and drop ignored\nPlease try again.");
	}
	private String fixLinuxString(String s) {
		StringBuffer sb = new StringBuffer(200);
		for (int i=0; i<s.length(); i+=2)
			sb.append(s.charAt(i));
		return new String(sb);
	}

	private String parseHTML(String s) {
		if (IJ.debugMode) IJ.log("parseHTML:\n"+s);
		int index1 = s.indexOf("src=\"");
		if (index1>=0) {
			int index2 = s.indexOf("\"", index1+5);
			if (index2>0)
				return s.substring(index1+5, index2);
		}
		index1 = s.indexOf("href=\"");
		if (index1>=0) {
			int index2 = s.indexOf("\"", index1+6);
			if (index2>0)
				return s.substring(index1+6, index2);
		}
		return s;
	}

	
	public void dragEnter(DropTargetDragEvent e)  {
		l.setText("Drop here!");
		e.acceptDrag(DnDConstants.ACTION_COPY);
	}
	
	public void dragOver(DropTargetDragEvent e) {
		l.setText("Drop here!");
	}
	
	public void dragExit(DropTargetEvent e) {
		l.setText("Drop Javascripts here");
	}
	
	public void dropActionChanged(DropTargetDragEvent e) {}
	
	// Runnable method: called after drag and drop
	public void run() {
		Iterator iterator = this.iterator;
		int cIndex;
		while(iterator.hasNext()) {
			Object obj = iterator.next();
			try {
				File f = (File)obj;
				//String path = f.getCanonicalPath();
				IJ.log(f.getCanonicalPath());
				addtoChoiceList(f);
				cIndex = c.getSelectedIndex();
			} catch (Throwable e) {
				if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
					IJ.handleException(e);
			}
		}
		l.setText("Drag JS to run");
	}
	
	public void addtoChoiceList(File f){
		String filename = f.getName();
		String filepath;
		try {
			filepath = f.getCanonicalPath();
		} catch (IOException e) {

			if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
				IJ.handleException(e);			
			e.printStackTrace();
			return;
		}
		if (isNotDroppedYet){
			c.removeAll();
			//exts.removeAll();
			paths.removeAll();
			isNotDroppedYet = false;
		}
		c.addItem(filename);
		//exts.addItem("");
		paths.addItem(filepath);
	}
	
	// ActionListener method: edit button pushed
	// TODO convert this to run
	// add maybe another button to edit?
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		int cIndex;
		if (source==b) {
			cIndex = c.getSelectedIndex();
			IJ.runMacroFile(paths.getItem(cIndex));
		}
		if (source == clear){
			c.removeAll();
			//exts.removeAll();
			paths.removeAll();			
		}
		if (source == code){
			cIndex = c.getSelectedIndex();
			IJ.run("Edit...", "open=" + paths.getItem(cIndex));
		}

	}
	
}
