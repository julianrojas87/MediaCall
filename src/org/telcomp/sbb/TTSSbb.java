package org.telcomp.sbb;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import javax.slee.ActivityContextInterface;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;

public abstract class TTSSbb implements javax.slee.Sbb {
	
	private static final String ttsUrl = "http://tts-api.com/tts.mp3?q=";
	private static final String filesPath = "/usr/local/Mobicents-MMS/media/Text2Speech/";
	private static final String tempFilesPath = "/usr/local/Mobicents-MMS/media/Text2Speech/temp/";
	private static final String lameCommand = "lame -b 32 --resample 8 ";
	private static final String mpg123Command = "mpg123 -m -w ";
	private static final String proxy = "proxy.unicauca.edu.co";
	private static final String proxyPort = "3128";
	private static boolean proxyNeeded = true;
	private static final int timeout = 5500;
	private static final String telcompSignature = ". This was a message provided by telcomp services." +
				" Telcomp development team wish you a nice day. Bye bye!";
	
	public String createAudioFile(String text){
		//Setting System proxy
		this.proxyConf(proxyNeeded);
		//Creating Text to convert
		String temp = text.concat(telcompSignature);		
		String a = temp.replaceAll("\\r\\n|\\n|\\r", "");
		String textToConvert = a.replaceAll(" ", "+");
		String returnString = null;
		try{
			//Testing TTS-API availability
			if(this.ping(ttsUrl+textToConvert, timeout)){
				//Count number of files present in the directory
				int files = new File(filesPath).list().length;
				//Create new audio file
				File f = new File(tempFilesPath + "audio"+files+".mp3");
				f.createNewFile();
				//Invoking TTS Web service
				URL website = new URL(ttsUrl+textToConvert);
				ReadableByteChannel rbc = Channels.newChannel(website.openStream());
			    FileOutputStream fos = new FileOutputStream(f.getPath());
			    fos.getChannel().transferFrom(rbc, 0, 1 << 24);
			    fos.close();
			    //Resampling and converting downloaded audio file
			    Runtime run = Runtime.getRuntime();
			    Process p = run.exec(lameCommand + tempFilesPath + f.getName() + " " + tempFilesPath + "tempAudio"+files+".mp3");
			    p.waitFor();
			    p.destroy();
			    this.deleteAudioFile(tempFilesPath + f.getName());
			    Process p1 = run.exec(mpg123Command + filesPath + "audio"+files+".wav" + " " + tempFilesPath + "tempAudio"+files+".mp3");
			    p1.waitFor();
			    p1.destroy();
			    this.deleteAudioFile(tempFilesPath + "tempAudio"+files+".mp3");
			    returnString = filesPath+"audio"+files+".wav";
			} else{
				returnString = a.replaceAll("\\(|\\)|\\,", "");
			}
		} catch(Exception e){
			e.printStackTrace();
		}
		return returnString;
	}
	
	public void deleteAudioFile(String name){
		Runtime run = Runtime.getRuntime();
		try {
			Process p = run.exec("rm " + name);
			p.waitFor();
			p.destroy();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean ping(String url, int timeout) {
	    try {
	        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
	        connection.setConnectTimeout(timeout);
	        connection.setReadTimeout(timeout);
	        connection.setRequestMethod("HEAD");
	        int responseCode = connection.getResponseCode();
	        return (200 <= responseCode && responseCode <= 399);
	    } catch (Exception e) {
	        return false;
	    }
	}
	
	private void proxyConf(boolean needed) {
		if (needed) {
			System.getProperties().put("proxy", "true");
	        System.getProperties().put("proxyHost", proxy);
	        System.getProperties().put("proxyPort", proxyPort);
	    }
	}


	
	// TODO: Perform further operations if required in these methods.
	public void setSbbContext(SbbContext context) { this.sbbContext = context;}
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
