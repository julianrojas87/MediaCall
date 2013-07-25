package org.telcomp.events;

import java.util.HashMap;
import java.util.Random;
import java.io.Serializable;

public final class StartMediaCallTelcoServiceEvent implements Serializable {

	private static final long serialVersionUID = 1L;
	private String uriSip;
	private String text;

	public StartMediaCallTelcoServiceEvent(HashMap<String, ?> hashMap) {
		id = new Random().nextLong() ^ System.currentTimeMillis();
		this.uriSip = (String) hashMap.get("uriSip");
		this.text = (String) hashMap.get("text");
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (o == null) return false;
		return (o instanceof StartMediaCallTelcoServiceEvent) && ((StartMediaCallTelcoServiceEvent)o).id == id;
	}
	
	public int hashCode() {
		return (int) id;
	}
	
	public String getUriSip(){
		return this.uriSip;
	}
	
	public String getText(){
		return this.text;
	}
	
	public String toString() {
		return "StartMediaCallEvent[" + hashCode() + "]";
	}

	private final long id;
}
