package org.telcomp.sbb;

import gov.nist.javax.sip.clientauthutils.UserCredentials;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sip.ClientTransaction;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.ActivityContextInterface;
import javax.slee.ChildRelation;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;

import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

import org.telcomp.auth.AccountManagerImpl;
import org.telcomp.auth.MessageDigestAlgorithm;
import org.telcomp.events.EndMediaCallTelcoServiceEvent;
import org.telcomp.events.StartMediaCallTelcoServiceEvent;

public abstract class CallManagerSbb implements javax.slee.Sbb {
	
	private final String mediaServerAddress = System.getProperty("jboss.bind.address");
	
	private SipActivityContextInterfaceFactory sipActivityContextInterfaceFactory;
	private SleeSipProvider sipFactoryProvider;
	private AddressFactory addressFactory;
	private HeaderFactory headerFactory;
	private MessageFactory messageFactory;
	private String uriSip;
	private CallIdHeader callid;
	private String requestUri;

	public void onStartMediaCallTelcoServiceEvent(StartMediaCallTelcoServiceEvent event, ActivityContextInterface aci) {
		this.setMainAci(aci);

		try {
			ChildRelation childRelation = this.getTTSSbb();
			TTSSbbLocalObject ttsSbb = (TTSSbbLocalObject) childRelation.create();
			this.setTextToSpeech(ttsSbb.createAudioFile(event.getText()));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		uriSip = event.getUriSip();
		DialogActivity outgoingDialog = this.fireInviteRequest(event.getUriSip(), false, null);
		ActivityContextInterface outgoingDialogAci = sipActivityContextInterfaceFactory.getActivityContextInterface(outgoingDialog);
		outgoingDialogAci.attach(this.sbbContext.getSbbLocalObject());
	}

	public void on1xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		System.out.println(event.getResponse().getStatusCode()+" Response received \n");
	}

	public void on2xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		CSeqHeader Cseq = (CSeqHeader) event.getResponse().getHeader(CSeqHeader.NAME);
		ToHeader to = (ToHeader) event.getResponse().getHeader(ToHeader.NAME);
		//Creating and sending ACK for 200 OK from Invite request
		DialogActivity out = (DialogActivity) aci.getActivity();

		try {
			Request ACK = out.createAck(Cseq.getSeqNumber());
			ACK.setRequestURI(to.getAddress().getURI());
			out.sendAck(ACK);
			//Creating Media Connection
			String remoteSdp = new String(event.getResponse().getRawContent());
			ChildRelation childRelation = this.getMediaSbb();
			MediaSbbLocalObject mediaSbb = (MediaSbbLocalObject) childRelation.create();
			ActivityContextInterface mediaAci = mediaSbb.createConnection(remoteSdp, this.getTextToSpeech());
			this.setMediaAci(mediaAci);
			this.setMediaLocalObject(mediaSbb);
			
			HashMap<String, Object> operationInputs = new HashMap<String, Object>();
			operationInputs.put("commited", (String) "true");
			EndMediaCallTelcoServiceEvent endEvent = new EndMediaCallTelcoServiceEvent(operationInputs);
			this.fireEndMediaCallTelcoServiceEvent(endEvent, this.getMainAci(), null);
			this.getMainAci().detach(this.sbbContext.getSbbLocalObject());
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}

	public void on4xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		if(event.getResponse().getStatusCode() == Response.UNAUTHORIZED){
			ClientTransaction ct = null;
			WWWAuthenticateHeader authHeader = (WWWAuthenticateHeader) event.getResponse().getHeader(WWWAuthenticateHeader.NAME);
			AuthorizationHeader newauth = this.getAuthorization(Request.INVITE, requestUri, "", authHeader, 
					new AccountManagerImpl().getCredentials(ct, "String"));
			System.out.println("New Authorization Header: "+ newauth.toString());
			DialogActivity outgoingDialog = this.fireInviteRequest(uriSip, true, newauth);
			ActivityContextInterface outgoingDialogAci = sipActivityContextInterfaceFactory.getActivityContextInterface(outgoingDialog);
			outgoingDialogAci.attach(this.sbbContext.getSbbLocalObject());
			aci.detach(this.sbbContext.getSbbLocalObject());
		} else{
			//User occupied
			HashMap<String, Object> operationInputs = new HashMap<String, Object>();
			operationInputs.put("commited", (String) "false");
			EndMediaCallTelcoServiceEvent endEvent = new EndMediaCallTelcoServiceEvent(operationInputs);
			this.fireEndMediaCallTelcoServiceEvent(endEvent, this.getMainAci(), null);
			this.getMainAci().detach(this.sbbContext.getSbbLocalObject());
		}
	}

	public void on6xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		//User rejected the call
		HashMap<String, Object> operationInputs = new HashMap<String, Object>();
		operationInputs.put("commited", (String) "false");
		EndMediaCallTelcoServiceEvent endEvent = new EndMediaCallTelcoServiceEvent(operationInputs);
		this.fireEndMediaCallTelcoServiceEvent(endEvent, this.getMainAci(), null);
		this.getMainAci().detach(this.sbbContext.getSbbLocalObject());
	}

	public void onBYE(RequestEvent event, ActivityContextInterface aci) {
		try {
			//Deleting Media Server Connection
			if(this.getMediaAci() != null){
				this.getMediaLocalObject().deleteConnection(this.getMediaAci());
			}
			//Sending 200 OK to BYE Request
			ServerTransaction st = event.getServerTransaction();
			Response response = messageFactory.createResponse(Response.OK, event.getRequest());
			st.sendResponse(response);
			aci.detach(this.sbbContext.getSbbLocalObject());
			//Deleting used audio file
			try {
				ChildRelation childRelation = this.getTTSSbb();
				TTSSbbLocalObject ttsSbb = (TTSSbbLocalObject) childRelation.create();
				ttsSbb.deleteAudioFile(this.getTextToSpeech());
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private DialogActivity fireInviteRequest(String toUri, boolean auth, AuthorizationHeader authHeader){
		DialogActivity out = null;
		Request inviteReq = null;
		
		Address addressTo;
		Address addressFrom;
		Address addressContact;
		ToHeader to;
		List<ViaHeader> vias;
		CallIdHeader callId;
		SipURI requestURI;
		
		try{
			addressFrom = addressFactory.createAddress("sip:TelcompServices@"+System.getProperty("jboss.bind.address")+":5060");
			//addressFrom = addressFactory.createAddress("sip:8334901@190.5.200.20");
			addressFrom.setDisplayName("TelcompServices");
			FromHeader from = headerFactory.createFromHeader(addressFrom, "TelcompServices" + (Math.random() * 100000));
			CSeqHeader cshead = headerFactory.createCSeqHeader(1L, Request.INVITE); 
			MaxForwardsHeader maxhead = headerFactory.createMaxForwardsHeader(70);
			SipURI contactURI = addressFactory.createSipURI(((SipURI) from.getAddress().getURI()).getUser(), 
					sipFactoryProvider.getListeningPoints()[0].getIPAddress() + ":5060");
			addressContact = addressFactory.createAddress(contactURI);
			ContactHeader contact = headerFactory.createContactHeader(addressContact);
			addressTo = addressFactory.createAddress(toUri);
			addressTo.setDisplayName(this.getName(toUri));
			vias = new ArrayList<ViaHeader>(1);
			vias.add(sipFactoryProvider.getLocalVia("UDP", "z9hC4GbK095871331.0"));
			to = headerFactory.createToHeader(addressTo, null);			 
			if (auth) {
				callId = callid;
				cshead.setSeqNumber(2L);
			} else {
				callId = sipFactoryProvider.getNewCallId();
				callid = callId;
			}
			requestURI = (SipURI)to.getAddress().getURI();
			inviteReq = messageFactory.createRequest(requestURI, Request.INVITE, callId, cshead, from, to, vias, maxhead);
			inviteReq.addHeader(contact);
			inviteReq = this.setSDP(inviteReq);
			requestUri = inviteReq.getRequestURI().toString();
			if (auth) {
				inviteReq.addHeader(authHeader);
			}
			out = sipFactoryProvider.getNewDialog(addressFrom, addressTo);
			out.sendRequest(inviteReq);
		} catch(Exception e){
			e.printStackTrace();
		}
		return out;
	}
	
	private AuthorizationHeader getAuthorization(String method, String uri, String requestBody, 
			WWWAuthenticateHeader authHeader, UserCredentials userCredentials) {
		String response = null;

		// JvB: authHeader.getQop() is a quoted _list_ of qop values
		// (e.g. "auth,auth-int") Client is supposed to pick one
		String qopList = authHeader.getQop();
		String qop = (qopList != null) ? "auth" : null;
		String nc_value = "00000001";
		String cnonce = "xyz";

		response = MessageDigestAlgorithm.calculateResponse(
				authHeader.getAlgorithm(), userCredentials.getUserName(),
				authHeader.getRealm(), userCredentials.getPassword(),
				authHeader.getNonce(), nc_value, cnonce, method, uri,
				requestBody, qop);

		AuthorizationHeader authorization = null;
		try {
			if (authHeader instanceof ProxyAuthenticateHeader) {
				authorization = headerFactory.createProxyAuthorizationHeader(authHeader.getScheme());
			} else {
				authorization = headerFactory.createAuthorizationHeader(authHeader.getScheme());
			}

			authorization.setUsername(userCredentials.getUserName());
			authorization.setRealm(authHeader.getRealm());
			authorization.setNonce(authHeader.getNonce());
			authorization.setParameter("uri", uri);
			authorization.setResponse(response);
			if (authHeader.getAlgorithm() != null) {
				authorization.setAlgorithm(authHeader.getAlgorithm());
			}

			if (authHeader.getOpaque() != null) {
				authorization.setOpaque(authHeader.getOpaque());
			}

			// jvb added
			if (qop != null) {
				authorization.setQop(qop);
				authorization.setCNonce(cnonce);
				authorization.setNonceCount(Integer.parseInt(nc_value));
			}

			authorization.setResponse(response);

		} catch (ParseException ex) {
			throw new RuntimeException(
					"Failed to create an authorization header!");
		}

		return authorization;
	}
	
	// Get User name from URI
	private String getName(String prevName) {
		return prevName.substring(prevName.indexOf(':') + 1, prevName.indexOf('@'));
	}
	
	private Request setSDP(Request request){
		String falseSDP = "v=0\r\n" + "o=MediaServer 15773502 15773502" + " IN IP4 "+this.mediaServerAddress+"\r\n" 
				+ "s=session\r\n" + "c=IN IP4 "+this.mediaServerAddress+"\r\n" + "t=0 0\r\n" + "m=audio 1666 RTP/AVP 0 8\r\n" 
				+ "a=rtpmap:0 PCMU/8000\r\n" + "a=rtpmap:8 PCMA/8000\r\n";
		ContentTypeHeader contentTypeHeader;
		try {
			contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
			byte[] contents = falseSDP.getBytes();
			request.setContent(contents, contentTypeHeader);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return request;
	}


	
	// TODO: Perform further operations if required in these methods.
	public void setSbbContext(SbbContext context) {
		this.sbbContext = context;
		try {
			Context ctx = (Context) new InitialContext().lookup("java:comp/env");
			sipActivityContextInterfaceFactory = (SipActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainsip/1.2/acifactory");
			sipFactoryProvider = (SleeSipProvider) ctx.lookup("slee/resources/jainsip/1.2/provider");
			addressFactory = sipFactoryProvider.getAddressFactory();
			headerFactory = sipFactoryProvider.getHeaderFactory();
			messageFactory = sipFactoryProvider.getMessageFactory();
		} catch (NamingException e) {
			e.printStackTrace();
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
	
	public abstract void fireEndMediaCallTelcoServiceEvent(EndMediaCallTelcoServiceEvent event, ActivityContextInterface aci, javax.slee.Address address);
	
	// 'outgoinDialog' CMP field setter
	public abstract void setOutgoinDialog(ActivityContextInterface value);
	// 'outgoinDialog' CMP field getter
	public abstract ActivityContextInterface getOutgoinDialog();
	
	// 'mainAci' CMP field setter
	public abstract void setMainAci(ActivityContextInterface value);
	// 'mainAci' CMP field getter
	public abstract ActivityContextInterface getMainAci();
	
	// 'textToSpeech' CMP field setter
	public abstract void setTextToSpeech(String value);
	// 'textToSpeech' CMP field getter
	public abstract String getTextToSpeech();
	
	// 'mediaAci' CMP field setter
	public abstract void setMediaAci(ActivityContextInterface value);
	// 'mediaAci' CMP field getter
	public abstract ActivityContextInterface getMediaAci();
	
	// 'mediaLocalObject' CMP field setter
	public abstract void setMediaLocalObject(MediaSbbLocalObject value);
	// 'mediaLocalObject' CMP field getter
	public abstract MediaSbbLocalObject getMediaLocalObject();

	public abstract ChildRelation getMediaSbb();
	public abstract ChildRelation getTTSSbb();

	
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
