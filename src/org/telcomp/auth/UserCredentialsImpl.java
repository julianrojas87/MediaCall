package org.telcomp.auth;

import gov.nist.javax.sip.clientauthutils.UserCredentials;

public class UserCredentialsImpl implements UserCredentials{
	
	private String userName;
    private String sipDomain;
    private String password;

    public UserCredentialsImpl(String userName, String sipDomain, String password) {
        this.userName = userName;
        this.sipDomain = sipDomain;
        this.password = password;
    }

	@Override
	public String getPassword() {
		// TODO Auto-generated method stub
		return password;
	}

	@Override
	public String getSipDomain() {
		// TODO Auto-generated method stub
		return sipDomain;
	}

	@Override
	public String getUserName() {
		// TODO Auto-generated method stub
		return userName;
	}

}
