package org.telcomp.sbb;

import jain.protocol.ip.mgcp.JainMgcpEvent;
import jain.protocol.ip.mgcp.message.CreateConnection;
import jain.protocol.ip.mgcp.message.CreateConnectionResponse;
import jain.protocol.ip.mgcp.message.DeleteConnection;
import jain.protocol.ip.mgcp.message.DeleteConnectionResponse;
import jain.protocol.ip.mgcp.message.NotificationRequest;
import jain.protocol.ip.mgcp.message.NotificationRequestResponse;
import jain.protocol.ip.mgcp.message.Notify;
import jain.protocol.ip.mgcp.message.NotifyResponse;
import jain.protocol.ip.mgcp.message.parms.CallIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConflictingParameterException;
import jain.protocol.ip.mgcp.message.parms.ConnectionDescriptor;
import jain.protocol.ip.mgcp.message.parms.ConnectionIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConnectionMode;
import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;
import jain.protocol.ip.mgcp.message.parms.EventName;
import jain.protocol.ip.mgcp.message.parms.NotifiedEntity;
import jain.protocol.ip.mgcp.message.parms.RequestedAction;
import jain.protocol.ip.mgcp.message.parms.RequestedEvent;
import jain.protocol.ip.mgcp.message.parms.ReturnCode;
import jain.protocol.ip.mgcp.pkg.MgcpEvent;
import jain.protocol.ip.mgcp.pkg.PackageName;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.slee.*;

import net.java.slee.resource.mgcp.JainMgcpProvider;
import net.java.slee.resource.mgcp.MgcpActivityContextInterfaceFactory;
import net.java.slee.resource.mgcp.MgcpConnectionActivity;
import net.java.slee.resource.mgcp.MgcpEndpointActivity;

public abstract class MediaSbb implements javax.slee.Sbb {
	
	private static final String endpointName = "/mobicents/media/aap/$";
	private static final String mmsAddress = System.getProperty("jboss.bind.address");
	private static final String sleeAddress = System.getProperty("jboss.bind.address");
	private static final int mgcpPortGateway = 2427;
	private static final int mgcpPortCallagent = 2727;
	//private static final String textToSpeechVoice = "kevin16";
	
	private JainMgcpProvider jainMgcpProvider;
	private MgcpActivityContextInterfaceFactory mgcpACIFactory;
	
	public ActivityContextInterface createConnection(String remoteSdp, String text) {
		System.out.println("Creating Media Connection...");
		this.setRemoteSdp(remoteSdp);
		this.setTextToSpeech(text);
		// Get call identifier and store it in CMP field
		CallIdentifier callID = jainMgcpProvider.getUniqueCallIdentifier();
		setCallIdentifier(callID.toString());
		// Create a new end point identifier
		EndpointIdentifier enpointID = new EndpointIdentifier(endpointName, mmsAddress + ":" + mgcpPortGateway);
		// Create a connection with call and end point identifiers
		CreateConnection createConnection = new CreateConnection(this, callID, enpointID, ConnectionMode.SendOnly);
		// Set remote connection descriptor
		try {
			createConnection.setRemoteConnectionDescriptor(new ConnectionDescriptor(this.getRemoteSdp()));
		} catch (ConflictingParameterException e) {
			System.out.println("failed to set remote connection descriptor "+e.toString());
		}
		// Set connection transaction handler
		int transactionHandlerID = jainMgcpProvider.getUniqueTransactionHandler();
		createConnection.setTransactionHandle(transactionHandlerID);
		System.out.println("Connection Created >> callID: "+ getCallIdentifier() + "; endpointID: "+ enpointID.toString());
		// Create MGCP connection activity
		MgcpConnectionActivity mgcpConnectionActivity = jainMgcpProvider.getConnectionActivity(transactionHandlerID, enpointID);
		// Get activity context interface from MGCP connection activity
		ActivityContextInterface mgcpConnectionACI = null;
		try {
			mgcpConnectionACI = mgcpACIFactory.getActivityContextInterface(mgcpConnectionActivity);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// Attach this SBB local object to MGCP ACI
		mgcpConnectionACI.attach(sbbContext.getSbbLocalObject());
		this.setMgcpAci(mgcpConnectionACI);
		// Send MGCP events
		try{
			jainMgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { createConnection });
		} catch(Exception e){
			e.printStackTrace();
		}
		return mgcpConnectionACI;
	}

	public void onCreateConnectionResponse(CreateConnectionResponse event, ActivityContextInterface aci) {
		System.out.println("Received CreateConnectionResponse: " + event.getTransactionHandle());
		ReturnCode status = event.getReturnCode();
		
		switch (status.getValue()) {
			case ReturnCode.TRANSACTION_EXECUTED_NORMALLY:
				this.setEndPointName(event.getSpecificEndpointIdentifier().getLocalEndpointName());
				System.out.println("EndpointName: " + this.getEndPointName());
				ConnectionIdentifier connectionIdentifier = event.getConnectionIdentifier();
				this.setConnectionIdentifier(connectionIdentifier.toString());
				this.playMessageMedia();
			break;
			
			default:
				System.out.println("ERROR: Transaction failed: "+status.toString());
			break;
		}
	}
	
	private void playMessageMedia() {
		System.out.println("PLAY MESSAGE");
		// Send RQNT
		this.sendRQNT(this.getTextToSpeech(), true);
	}
	
	private void sendRQNT(String textToSpeech, boolean createActivity) {
		EndpointIdentifier endpointID = new EndpointIdentifier(this.getEndPointName(), mmsAddress + ":" + mgcpPortGateway);
		NotificationRequest notificationRequest = new NotificationRequest(this, endpointID, jainMgcpProvider.getUniqueRequestIdentifier());
		ConnectionIdentifier connectionID = new ConnectionIdentifier(getConnectionIdentifier());
		if(this.getTextToSpeech().endsWith(".wav")){
			String text = "file://"+this.getTextToSpeech();
			EventName[] signalRequests = { new EventName(PackageName.Announcement, MgcpEvent.ann.withParm(text), connectionID) };
			notificationRequest.setSignalRequests(signalRequests);
		} else {
			EventName[] signalRequests = { new EventName(PackageName.Announcement, MgcpEvent.ann.withParm("ts("+this.getTextToSpeech()+") vc(mbrola_us1)"), connectionID) };
			notificationRequest.setSignalRequests(signalRequests);
		}
		
		RequestedAction[] actions = { RequestedAction.NotifyImmediately };
		RequestedEvent[] requestedEvents = {
				new RequestedEvent(new EventName(PackageName.Announcement,
						MgcpEvent.oc, connectionID), actions),
				new RequestedEvent(new EventName(PackageName.Announcement,
						MgcpEvent.of, connectionID), actions) };
		notificationRequest.setRequestedEvents(requestedEvents);
		notificationRequest.setTransactionHandle(this.jainMgcpProvider.getUniqueTransactionHandler());
		NotifiedEntity notifiedEntity = new NotifiedEntity(mmsAddress, sleeAddress, mgcpPortCallagent);
		notificationRequest.setNotifiedEntity(notifiedEntity);

		if (createActivity) {
			MgcpEndpointActivity mgcpEnpointActivity = jainMgcpProvider.getEndpointActivity(endpointID);
			ActivityContextInterface mgcpEndpointACI = null;
			try {
				mgcpEndpointACI = mgcpACIFactory.getActivityContextInterface(mgcpEnpointActivity);
			} catch (Exception e) {
				e.printStackTrace();
			}
			mgcpEndpointACI.attach(sbbContext.getSbbLocalObject());
		}
		jainMgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { notificationRequest });
	}

	public void onNotificationRequestResponse(NotificationRequestResponse event, ActivityContextInterface aci) {
		System.out.println("Received NotificationRequestResponse");
		ReturnCode status = event.getReturnCode();

		switch (status.getValue()) {
			case ReturnCode.TRANSACTION_EXECUTED_NORMALLY:
				System.out.println("The Announcement should have been started");
			break;
			
			default:
				ReturnCode rc = event.getReturnCode();
				System.out.println("RQNT failed. Value = " + rc.getValue() + " Comment = " + rc.getComment());
				this.deleteConnection(this.getMgcpAci());
			break;
		}
	}

	public void onNotify(Notify	event, ActivityContextInterface aci) {
		System.out.println("Notify Event Received");
		NotifyResponse notifyResponse = new NotifyResponse(event.getSource(),ReturnCode.Transaction_Executed_Normally);
		notifyResponse.setTransactionHandle(event.getTransactionHandle());
		this.jainMgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { notifyResponse });

		EventName[] observedEventArray = event.getObservedEvents();
		for (EventName observedEvent : observedEventArray) {
			switch (observedEvent.getEventIdentifier().intValue()) {
				case MgcpEvent.REPORT_ON_COMPLETION:
					System.out.println("Announcement completed, Repeating Announcement...");
					this.sendRQNT(this.getTextToSpeech(), false);
				break;
				
				case MgcpEvent.REPORT_FAILURE:
					System.out.println("\tAnnouncement failed");
					this.deleteConnection(this.getMgcpAci());
				break;
				
				default:
					System.out.println("\tUnrecognized event");
					this.deleteConnection(this.getMgcpAci());
				break;
			}
		}
	}
	
	public void onDeleteConnectionResponse(DeleteConnectionResponse event, ActivityContextInterface aci) {
		System.out.println("DeleteConnectionResponse received");
		ReturnCode returnCode = event.getReturnCode();
		
		switch (returnCode.getValue()) {
			case ReturnCode.TRANSACTION_EXECUTED_NORMALLY:
				System.out.println("connection deleted");
				aci.detach(sbbContext.getSbbLocalObject());
			break;
			
			default:
				System.out.println("delete connection failed");
				aci.detach(sbbContext.getSbbLocalObject());
			break;
		}
	}
	
	public void deleteConnection(ActivityContextInterface mgcpAci){
		System.out.println("Deleting Connection...");
		MgcpConnectionActivity activity = (MgcpConnectionActivity) mgcpAci.getActivity();
		// Delete connection
		DeleteConnection deleteConnection = new DeleteConnection(this, activity.getEndpointIdentifier());
		System.out.println("EndpointIdentifier: "+activity.getEndpointIdentifier().toString());
		deleteConnection.setTransactionHandle(jainMgcpProvider.getUniqueTransactionHandler());
		jainMgcpProvider.sendMgcpEvents(new JainMgcpEvent[] { deleteConnection });
	}
	
	
	// TODO: Perform further operations if required in these methods.
	public void setSbbContext(SbbContext context) { 
		this.sbbContext = context; 
		try {
			Context ctx = (Context) new InitialContext().lookup("java:comp/env");
			jainMgcpProvider = (JainMgcpProvider) ctx.lookup("slee/resources/jainmgcp/2.0/provider/demo");
			mgcpACIFactory = (MgcpActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainmgcp/2.0/acifactory/demo");
		} catch (Exception ne) {
			System.out.println("Could not set SBB context: "+ne.toString());
		}
	}
    public void unsetSbbContext() { this.sbbContext = null; }
    
    // TODO: Implement the lifecycle methods if required
    public void sbbCreate() throws javax.slee.CreateException {}
    public void sbbPostCreate() throws javax.slee.CreateException {}
    public void sbbActivate() {}
    public void sbbPassivate() {}
    public void sbbRemove() {}
    public void sbbLoad() {}
    public void sbbStore() {}
    public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface activity) {}
    public void sbbRolledBack(RolledBackContext context) {}
	
    // 'mgcpAci' CMP field setter
 	public abstract void setMgcpAci(ActivityContextInterface value);
 	// 'mgcpAci' CMP field getter
 	public abstract ActivityContextInterface getMgcpAci();
    
    // 'connectionIdentifier' CMP field setter
	public abstract void setConnectionIdentifier(String value);
	// 'connectionIdentifier' CMP field getter
	public abstract String getConnectionIdentifier();

	// 'callIdentifier' CMP field setter
	public abstract void setCallIdentifier(String value);
	// 'callIdentifier' CMP field getter
	public abstract String getCallIdentifier();

	// 'remoteSdp' CMP field setter
	public abstract void setRemoteSdp(String value);
	// 'remoteSdp' CMP field getter
	public abstract String getRemoteSdp();

	// 'endPointName' CMP field setter
	public abstract void setEndPointName(String value);
	// 'endPointName' CMP field getter
	public abstract String getEndPointName();
	
	// 'textToSpeech' CMP field setter
	public abstract void setTextToSpeech(String value);
	// 'textToSpeech' CMP field getter
	public abstract String getTextToSpeech();


	
	/**
	 * Convenience method to retrieve the SbbContext object stored in setSbbContext.
	 * 
	 * TODO: If your SBB doesn't require the SbbContext object you may remove this 
	 * method, the sbbContext variable and the variable assignment in setSbbContext().
	 *
	 * @return this SBB's SbbContext object
	 */
	
	protected SbbContext getSbbContext() {
		return sbbContext;
	}

	private SbbContext sbbContext; // This SBB's SbbContext

}
