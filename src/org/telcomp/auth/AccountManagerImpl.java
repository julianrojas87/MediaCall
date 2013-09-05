package org.telcomp.auth;

import javax.sip.ClientTransaction;

import gov.nist.javax.sip.clientauthutils.AccountManager;
import gov.nist.javax.sip.clientauthutils.UserCredentials;

public class AccountManagerImpl implements AccountManager {

	@Override
	public UserCredentials getCredentials(ClientTransaction arg0, String arg1) {
		// TODO Auto-generated method stub
		return new UserCredentialsImpl("8334901","asterisk","unicauca##2013$$");
	}

}
