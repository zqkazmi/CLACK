

import java.applet.Applet;
import net.clackrouter.gui.ClackFramework;
import net.clackrouter.gui.ClackFrameworkHelper;

/**
 *  This file loads Clack and informs it about your RIP class. 
 *  Do not modify this file.  
 */
public class RIPClackLoader extends Applet {

	//When launched as an Applet
	public void init() {
		String[] params = {};
		if(getParameter(ClackFramework.PARAM_STRING) != null)
			params = getParameter(ClackFramework.PARAM_STRING).split(" ");
		load(params, this);
	}
	
	// When launched as an application
	public static void main(String[] args) {
		load(args, null);
	}
	
	public static void load(String[] args, Applet parent){

		ClackFramework framework = new ClackFramework(parent);
		framework.addAdditionalComponent("RIPRouting", "RIPRouting");
		ClackFrameworkHelper.configureClackFramework(args, framework);	

	}
	
}
