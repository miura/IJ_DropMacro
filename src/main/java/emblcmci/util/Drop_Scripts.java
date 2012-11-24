package emblcmci.util;

/** Drag  & Drop plugin for Script files.  
 * Kota Miura (miura@embl.de)
 * 
 * --- modified Dropfile_.java: original description is below ---
 * 
* This plug-in creates a frame that accepts drag and drop
* and calls the selected macro for each file.
* based on the default sample plugin frame and the builtin DragAndDrop.java plugin
* Jerome Mutterer and Wayne Rasband.
*/

import ij.IJ;
import ij.Macro;
import ij.WindowManager;
import ij.gui.GUI;
import ij.plugin.frame.PlugInFrame;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.FlowLayout;
import java.awt.Label;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import org.python.core.PyDictionary;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;

@SuppressWarnings("serial")
public class Drop_Scripts extends PlugInFrame implements DropTargetListener, Runnable, ActionListener, WindowListener, ItemListener, Observer {
	
	private Iterator iterator;
	Label dropzone = new Label();
	Choice scriptlist = new Choice();
	Choice paths = new Choice();
	Button runbutton = new Button();
	Button clear = new Button();
	Button code = new Button();
	Checkbox autorun = new Checkbox();

	//TODO consider changing this to the original location, or just omit saving such
	String defaultScriptsPath = IJ.getDirectory("imagej")+File.separator+"scripts"+File.separator;
	
	private boolean isNotDroppedYet = true;
	private boolean isPy = false;
	
	static FileChangeNotifier fcn;

	public Drop_Scripts() {
		super("DropScript");
		if (IJ.versionLessThan("1.43i")) return;
		dropzone.setText("Drop Scripts here");

		scriptlist.addItem("(no script yet)");
		scriptlist.addItemListener(this);
		runbutton.setLabel("Run");
		runbutton.addActionListener(this);
		clear.setLabel("Clear");
		clear.addActionListener(this);	
		code.setLabel("Code");
		code.addActionListener(this);
		autorun.setLabel("AutoRun");
		autorun.setEnabled(false);	//initially disabled, become enabled upon choosing a file
		autorun.addItemListener(this);
		setLayout (new FlowLayout ());
		add(dropzone);	add(scriptlist); add(runbutton); add(clear);add(code);add(autorun);
		pack();
		GUI.center(this);
		new DropTarget(this, this);
		WindowManager.addWindow(this);
		setVisible(true); // depreciated show() sends a warning
		
	}

	public void phantomJSGenerator(){
		IJ.log("creating a phantom js file");
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
	// Drop target Listener methods
	// triggered when file is dropped in the drop-of zone.
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
				} /*else if (flavors[i].isFlavorTextType()) {
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
				}*/
			}
			if (iterator!=null) {
				Thread thread = new Thread(this, "Drop_Script");
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
	public void dragEnter(DropTargetDragEvent e)  {
		dropzone.setText("Drop here!");
		e.acceptDrag(DnDConstants.ACTION_COPY);
	}
	
	public void dragOver(DropTargetDragEvent e) {
		dropzone.setText("Drop here!");
	}
	
	public void dragExit(DropTargetEvent e) {
		dropzone.setText("accepts .ijm, .js. .py");
	}
	
	public void dropActionChanged(DropTargetDragEvent e) {}
	
	// Runnable method: called after drag and drop
	public void run() {
		Iterator iterator = this.iterator;
		//int cIndex;
		while(iterator.hasNext()) {
			Object obj = iterator.next();
			try {
				File f = (File)obj;
				String path = f.getCanonicalPath();
				IJ.log(path);
				if (path.endsWith(".txt") || path.endsWith(".ijm") || path.endsWith(".js") || path.endsWith(".py") ){
					addtoChoiceList(f);
					if (path.endsWith(".py"))
						isPy  = true;
					else
						isPy = false;
				}
				//cIndex = c.getSelectedIndex();
			} catch (Throwable e) {
				if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
					IJ.handleException(e);
			}
		}
		dropzone.setText("Drag here to list");
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
			scriptlist.removeAll();
			paths.removeAll();
			isNotDroppedYet = false;
			
		}
		autorun.setEnabled(true);
		scriptlist.addItem(filename);
		paths.addItem(filepath);
	}
	
	// ActionListener method: edit button pushed
	// add maybe another button to edit?
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		int cIndex;
		if (paths.getItemCount()>0) {
			if (source==runbutton) {
				cIndex = scriptlist.getSelectedIndex();
				isPy = paths.getItem(cIndex).endsWith(".py");
				if (isPy) 
					runPY(paths.getItem(cIndex));
				else
					IJ.runMacroFile(paths.getItem(cIndex));
			}
			if (source == clear){
				scriptlist.removeAll();
				paths.removeAll();
				autorun.setEnabled(false);
				isNotDroppedYet =true;
			}
			if (source == code){
				cIndex = scriptlist.getSelectedIndex();
				IJ.run("Edit...", "open=" + paths.getItem(cIndex));
			}
		}

	}
	@Override
	public void itemStateChanged(ItemEvent i) {
		// need to implement this
		int cIndex = scriptlist.getSelectedIndex();
		String filepath = paths.getItem(cIndex);
	
		if (i.getSource() == autorun){
//		if (i.getSource() != autorun){	
			if (autorun.getState()){
				fcn = new FileChangeNotifier(filepath);
				fcn.addObserver(this);
			} else {
				fcn.deleteObserver(this);
				fcn = null;
			}
		}
		if (i.getSource() == scriptlist){
			// if fcn is already alive, then delete all observers first.
			if ((scriptlist.getItemCount() > 0) && autorun.getState()){
				if (fcn != null)
					fcn = null;
				fcn = new FileChangeNotifier(filepath);
				fcn.addObserver(this);
			}
		}
		
	}
	@Override
	public void update(Observable arg0, Object arg1) {
		int cIndex = scriptlist.getSelectedIndex();
		isPy = paths.getItem(cIndex).endsWith(".py");
		if (isPy) 
			runPY(paths.getItem(cIndex));
		else
			IJ.runMacroFile(paths.getItem(cIndex));
	}
	
    /** Run the Jython script at @param path */
    public boolean runPY(String path) {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	ByteArrayOutputStream baosError = new ByteArrayOutputStream();
 //       ScriptRunner.runPY(path, null);
    	Calendar cal = Calendar.getInstance();
    	cal.getTime();
    	SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd:HH:mm:ss");
    	IJ.log("--% " + sdf.format(cal.getTime()) + " %--");
        try {
            PySystemState pystate = new PySystemState();
            pystate.setClassLoader(IJ.getClassLoader());
            PythonInterpreter pi = new PythonInterpreter(new PyDictionary(), pystate);
            pi.setErr(baosError);
            pi.setOut(baos);
            pi.execfile(path);
        } catch (Exception e) {
        	IJ.log(baos.toString());
            e.printStackTrace();
            IJ.log("!!!!! pity! need to dubug this script! !!!!!");
            IJ.log(baosError.toString());
            return false;
        }
        IJ.log(baos.toString());
        return true;
    }
    public void windowClosed(WindowEvent e) {
    	WindowManager.removeWindow(this);
    }
    public static void main(String[] args) {
    	Drop_Scripts ds = new Drop_Scripts();
    	
    	
    }
	
}
