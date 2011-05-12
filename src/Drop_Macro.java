import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.process.*;
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

public class Drop_Macro extends PlugInFrame implements DropTargetListener, Runnable, ActionListener {
	
	private Iterator iterator;
	Label l = new Label();
	Choice c = new Choice();
	Choice exts = new Choice();
	Choice paths = new Choice();
	Button b = new Button();
	Button clear = new Button();	
	//TODO consider changing this to the original location, or just omit saving such
	String defaultMacroPath = IJ.getDirectory("macros")+File.separator; //+"Droplet Actions"+File.separator;
	private boolean isNotDroppedYet = true; 

	public void phantomMacroGenerator(){
		String exampleMacro=" file=getArgument();\\n open(file);\\n run (\"8-bit\");";
		IJ.runMacro("f=File.open(getDirectory('macros')+"+"'Open as 8-bit.ijm'"+"); print(f,'"+exampleMacro+"');File.close(f);");
	}
	
	
	public Drop_Macro() {
		super("DropMacro");
		if (IJ.versionLessThan("1.43i")) return;
		l.setText("Drag a macro to run");
		File f = new File(defaultMacroPath);
		if (!f.exists()){ 
			f.mkdir();
			phantomMacroGenerator();
		}
		IJ.debugMode = true;		
		String[] list = f.list();	// a list of pre-existing macros
		if (list.length==0) 
			phantomMacroGenerator();
		for (int i=0; i<list.length; i++) {
			if (list[i].endsWith(".txt") || list[i].endsWith(".ijm")){
				paths.addItem(defaultMacroPath + list[i]);	
				exts.addItem(list[i].substring(list[i].length()-4));
				list[i] = list[i].substring(0, list[i].length()-4);
				c.addItem(list[i]);
				if (IJ.debugMode) IJ.log(list[i]);
			}
		}
		b.setLabel("Run");
		b.addActionListener(this);
		clear.setLabel("Clear");
		clear.addActionListener(this);		
		setLayout (new FlowLayout ());
		add(l);	add(c); add(b); add(clear);
		pack();
		GUI.center(this);
		new DropTarget(this, this);
		WindowManager.addWindow(this);
		setVisible(true); // depreciated show() sends a warning
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
				Thread thread = new Thread(this, "Drop_Macro");
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
		l.setText("Drag files to process");
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
		l.setText("Drag macro to run");
	}
	
	public void addtoChoiceList(File f){
		String filename = f.getName();
		String filepath;
		try {
			filepath = f.getCanonicalPath();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
				IJ.handleException(e);			
			e.printStackTrace();
			//filepath = null;
			return;
		}
		if (isNotDroppedYet){
			c.removeAll();
			exts.removeAll();
			paths.removeAll();
			isNotDroppedYet = false;
		}

		if (filename.endsWith(".txt")) {		
			filename = filename.substring(0, filename.length()-4);
			c.addItem(filename);
			exts.addItem(".txt");
			paths.addItem(filepath);
		} else if (filename.endsWith(".ijm")) {
			filename = filename.substring(0, filename.length()-4);
			c.addItem(filename);
			exts.addItem(".ijm");
			paths.addItem(filepath);
		} else {
			c.addItem(filename);
			exts.addItem("");
			paths.addItem(filepath);
		}
	}
	
	// ActionListener method: edit button pushed
	// TODO convert this to run
	// add maybe another button to edit?
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		//if (source==b) IJ.run("Edit...", "open=["+dropletActionsPath+c.getSelectedItem()+exts.getItem(c.getSelectedIndex())+"]");
		int cIndex;
		if (source==b) {
			cIndex = c.getSelectedIndex();
			IJ.runMacroFile(paths.getItem(cIndex));
		}
		if (source == clear){
			c.removeAll();
			exts.removeAll();
			paths.removeAll();			
		}

	}
	
}
