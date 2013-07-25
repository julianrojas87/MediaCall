package org.telcomp.events;

import java.util.HashMap;
import java.util.Random;
import java.io.Serializable;

public final class EndMediaCallTelcoServiceEvent implements Serializable {

	private static final long serialVersionUID = 1L;
	private boolean commited;

	public EndMediaCallTelcoServiceEvent(HashMap<String, ?> hashMap) {
		id = new Random().nextLong() ^ System.currentTimeMillis();
		this.commited = (boolean) Boolean.parseBoolean((String) hashMap.get("commited"));
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (o == null) return false;
		return (o instanceof EndMediaCallTelcoServiceEvent) && ((EndMediaCallTelcoServiceEvent)o).id == id;
	}
	
	public int hashCode() {
		return (int) id;
	}
	
	public boolean getCommited(){
		return this.commited;
	}
	
	public String toString() {
		return "EndMediaCallEvent[" + hashCode() + "]";
	}

	private final long id;
}
