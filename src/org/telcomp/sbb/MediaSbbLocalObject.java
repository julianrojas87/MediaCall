package org.telcomp.sbb;

import javax.slee.ActivityContextInterface;

public interface MediaSbbLocalObject extends javax.slee.SbbLocalObject {

	ActivityContextInterface createConnection(String remoteSdp, String textToSpeech);
	void deleteConnection(ActivityContextInterface mgcpACI);

}
