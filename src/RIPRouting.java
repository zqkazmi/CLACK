
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import net.clackrouter.component.base.*;
import net.clackrouter.netutils.*;
import net.clackrouter.packets.*;
import net.clackrouter.router.core.*;
import net.clackrouter.routing.*;

public class RIPRouting extends ClackComponent implements LocalLinkChangedListener {
	
	// do not modify these port values
	public static final int PORT_UPDATE_IN = 0, PORT_UPDATE_OUT = 1, NUM_PORTS = 2;
	
	// you may modify these values if needed
	public static int PERIODIC_UPDATE_MSEC = 10000; // 10 seconds	
	public static int TIMER_INTERVAL_MSEC = 1000; // 1 second	
	public static int INFINITE_COST = 16;
	public static short MAX_TTL = 20;
	public static short NO_TTL = -1;
	public int counter=0; 
	// pointer to the routing table used by this virtual router
	public RoutingTable mRtable;
	
	
// constructor: do not modify	
public RIPRouting(Router r, String name){
	super(r, name);	
	setupPorts(NUM_PORTS);
}


// initialization for this component: do not modify
public void initializeProperties(Properties props){
	
	mRtable = mRouter.getRoutingTable();
	add_local_routes();
	mRouter.updateRouteTableStateInGUI();
		
	// get updates from the router when our status changes. 
	mRouter.addLocalLinkChangeListener(this);
		
	// schedule first "alarm" for 3 seconds
	setAlarm(3 * TIMER_INTERVAL_MSEC);
}


// sets up component ports: do not modify 	
protected void setupPorts(int numports){
    	super.setupPorts(numports);
        String in_desc = "Routing updates address to local interfaces";
        String out_desc  = "Outgoing routing updates";
        m_ports[PORT_UPDATE_IN] = new ClackPort(this, PORT_UPDATE_IN, in_desc, 
			ClackPort.METHOD_PUSH, ClackPort.DIR_IN, IPPacket.class);
        m_ports[PORT_UPDATE_OUT]     = new ClackPort(this, PORT_UPDATE_OUT, out_desc, 
			ClackPort.METHOD_PUSH, ClackPort.DIR_OUT, IPPacket.class);
} // -- setupPorts
	

// adds local routes to the routing table of this router.  
private void add_local_routes(){
	try {
		Hashtable local_links = getRouter().getLocalLinkInfo();

		// Add a route for each of our local interfaces
		for(Enumeration e = local_links.elements(); e.hasMoreElements(); ){
	
			LocalLinkInfo info = (LocalLinkInfo) e.nextElement();		
			RIPRoutingEntry local_entry = RIPRoutingEntry.createLocalRIPRoutingEntry(info.address,
									info.subnet_mask, info.local_iface, info.metric);
			mRtable.addEntry(local_entry);
		}		
	} catch (Exception ex){ ex.printStackTrace();}			
}
	
	
// this function handles any RIP update packets sent to this router
public void acceptPacket(VNSPacket packet, int port_number) {
    if(port_number != PORT_UPDATE_IN) return;
    	
    IPPacket ip_packet = (IPPacket)packet;
    byte protocol = ip_packet.getProtocol();
            
    if(protocol != IPPacket.PROTO_CLACK_RIP) return;
     
    try { 
    	
    	RIPRoutingUpdate update = new RIPRoutingUpdate(ip_packet.getBodyBuffer());
    	InetAddress next_hop = InetAddress.getByAddress(ip_packet.getHeader().getSourceAddress().array());
	
    	// lookup your router's info about the link you heard this announcement on
    	LocalLinkInfo local_link_info = getLocalLinkInfoForNextHop(next_hop);
	
    	// array of all RIPRoutingUpdate.Entry objects from the packet
    	ArrayList<RIPRoutingUpdate.Entry> entries = update.getAllEntries();
	
    	// print out some information about the update you received
    	System.out.println(mRouter.getHost() + " received update with " 
    			+ entries.size() + " entries from neighbor " + 
    			local_link_info.next_hop.getHostAddress());


    	//TODO: delete the following line and IMPLEMENT UPDATE PROCESSING HERE....
    	////If it's doesn't exist, you'll want to add.
		//If it exists as a local route, you'll want to leave it be.
		//If it's of a different interface: replace if the new cost is less and don't bother if it's more.
		//IF it's of a same interface, you do replace as it needs to be updated. TTL may be the only thing updated. 
		//If it's the same interface and the cost is infinity, delete it from the table.
    	for(int i=0; i<entries.size(); i++){
    		RIPRoutingEntry prefix=(RIPRoutingEntry)mRtable.longestPrefixMatch(entries.get(i).net);
    		if(prefix==null){
    			RIPRoutingEntry entryFill=RIPRoutingEntry.createNonLocalRIPRoutingEntry(entries.get(i).net, 
    					entries.get(i).mask, next_hop, local_link_info.local_iface, entries.get(i).ttl,
    					entries.get(i).metric + local_link_info.metric);
    			if(entryFill.cost<16){
    				mRtable.addEntry(entryFill);
    				broadcastUpdate(); 
    			}
    		}
    		else if(!prefix.isLocal)
    		{
    			//if not local, we check if cost is higher or less. 
    			if(!local_link_info.local_iface.equals(prefix.interface_name))
    			{
    				//If the sum of (local_link_info.metric + entries.get(i).metric) is less than prefix.cost,
    				//we must replace every field in prefix.
    				if(local_link_info.metric + entries.get(i).metric < prefix.cost)
    				{
    					prefix.cost = local_link_info.metric + entries.get(i).metric;
    					prefix.network = entries.get(i).net;
    					prefix.interface_name = local_link_info.local_iface;
    					prefix.mask = entries.get(i).mask;
    					prefix.ttl = entries.get(i).ttl;
    					prefix.nextHop = next_hop;
    					broadcastUpdate();
    				}
    				
    			}
    			//If the interface name is the same, we simply have to update cost and ttl.
    			else
    			{
    				prefix.cost = entries.get(i).metric + local_link_info.metric;
    				prefix.ttl = entries.get(i).ttl;
    				broadcastUpdate();
    			}
    		}
    		if(prefix.cost >= 16)
    		{
    			mRtable.deleteEntry(prefix);
    			broadcastUpdate();
    		}
    	}
    } catch (UnknownHostException uhe) {
    	uhe.printStackTrace();
    }
            	
}
    
    
// Callback when the alarm from 'setAlarm()' expires
public void notifyAlarm(){

	//TODO: MODIFY THIS FUNCTION TO PERFORM PERIODIC FUNCTIONALITY
	//Go through table and check TTL value - decrement if greater than 0 and if it is zero, then it deletes entry. 
	for(int i=0; i<mRtable.numEntries(); i++){
		RIPRoutingEntry alarm=(RIPRoutingEntry)mRtable.getEntry(i); 
		if(alarm.ttl!=NO_TTL){
				if(alarm.ttl!=0){
					alarm.ttl--; 
				}
				else if(alarm.ttl==0){
					mRtable.deleteEntry(alarm); 
					System.out.println("Link Deleted");
				}
		}
		//Check if counter is 10, if it is broadcastUpdate otherwise make it zero. Increment if not at 10. 
		if(counter==10){
			broadcastUpdate(); 
			counter=0; 
		}
		else 
		{
			counter++; 
		}
		
	//System.out.println("Hello from notifyAlarm!");
    			
    // you will want to schedule another alarm
    setAlarm(TIMER_INTERVAL_MSEC); 
    setAlarm(PERIODIC_UPDATE_MSEC); 
    broadcastUpdate(); 
        
    //dumpRIPRoutingTable();
	// update Clack's GUI view of the routing table (DO NOT DELETE)
    mRouter.updateRouteTableStateInGUI();
	}
}
// A call-back function used by the Router to inform the RIP module if 
// any characteristics of the local links have been changed (e.g., a 
// link is disabled, or its metric is changed)
public void localLinkChanged(LocalLinkInfo old_info, LocalLinkInfo new_info){
    if(old_info.is_up != new_info.is_up){
    	if(new_info.is_up){
    		RIPRoutingEntry entry=RIPRoutingEntry.createLocalRIPRoutingEntry(new_info.address,new_info.subnet_mask, new_info.local_iface, new_info.metric); 
    		mRtable.addEntry(entry);
    		System.out.println(new_info.local_iface+" was added to the table");
    		broadcastUpdate(); 
    	}
    	else{
    		for(int i=0; i< mRtable.numEntries(); i++){
    			RIPRoutingEntry entry=(RIPRoutingEntry)mRtable.getEntry(i); 
    			if(entry.interface_name.equals(new_info.local_iface)){
    				i--; 
    				mRtable.deleteEntry(entry); 
    				System.out.println(new_info.local_iface+" was deleted.");
    				broadcastUpdate(); 
    			}
    		}
    	}
    	 
    }
    //Check if cost for local chnaged and update to new one if it has. 
    else if(old_info.metric!=new_info.metric){
    	if(new_info.metric<old_info.metric){
    		RIPRoutingEntry entry=RIPRoutingEntry.createLocalRIPRoutingEntry(new_info.address,new_info.subnet_mask, new_info.local_iface, new_info.metric); 
    		mRtable.addEntry(entry);
    		System.out.println(new_info.local_iface+" was added to the table");
    		broadcastUpdate(); 
    	}
    }

}

// utility function to find a LocalLinkInfo object associated with a nexthop IP address
protected LocalLinkInfo getLocalLinkInfoForNextHop(InetAddress next_hop){
		Hashtable local_links = getRouter().getLocalLinkInfo();
		for(Enumeration e = local_links.keys(); e.hasMoreElements(); ){
			
			String iface_str = (String) e.nextElement();
			LocalLinkInfo info = (LocalLinkInfo) local_links.get(iface_str);
			if(info.next_hop.equals(next_hop)) {
				return info;
			}
		}
		return null;
}


// utility function for printing the contents of a RIP routing table
// You do not need to call or modify this function, but feel free to make changes.      
public void dumpRIPRoutingTable(){

    	System.out.println("Routing table for " + mRouter.getHost() + " : (" + 
				mRtable.numEntries() + " entries)");
    	for(int i = 0; i < mRtable.numEntries(); i++){
    		RIPRoutingEntry rip_entry = (RIPRoutingEntry)mRtable.getEntry(i);
    		String prefix = NetUtils.NetworkAndMaskToString(rip_entry.network, rip_entry.mask);
    		if(rip_entry.nextHop != null){
    			System.out.println(prefix + " : " + rip_entry.nextHop.getHostAddress() + 
				" via " + rip_entry.interface_name
    				+ " cost = " + rip_entry.cost + " ttl = " + rip_entry.ttl);
    		}else {
    			System.out.println(prefix + " : * via " + rip_entry.interface_name
        				+ " cost = " + rip_entry.cost + " ttl = " + rip_entry.ttl);
    		}
    	}  
	}

/*
 * Broadcast updates made to the table.Go to each entry
 * Go through all the interfaces and update table.  
 */
public void broadcastUpdate(){ 
	try{
		Hashtable localLinks=getRouter().getLocalLinkInfo(); 
		for(Enumeration link=localLinks.elements(); link.hasMoreElements();){
			LocalLinkInfo RIP=(LocalLinkInfo)link.nextElement(); 
			RIPRoutingUpdate rUpdate=new RIPRoutingUpdate(); 
			
			if(RIP.is_up && RIP.is_routing_iface){
				for(int i=0; i<mRtable.numEntries(); i++){
					RIPRoutingEntry entry=(RIPRoutingEntry)mRtable.getEntry(i); 
					rUpdate.addEntry(entry.network, entry.mask, entry.cost, entry.ttl);
				}
			}
		}
	}catch (Exception e){
		e.printStackTrace();
	}
}
}

