package org.jenkinsci.plugins.sem2458;

import hudson.model.BuildListener;
import hudson.model.AbstractBuild;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.net.*;


public class BugLogger {
	public String TOKEN;
	public String title = "";
	public int buildNumber;
	public String event = "This is a test";
	public HashMap<String, String> paraMap;
	public int source;
	public String hostPort;
	public String buildURL;
	public String fogPrepend;
	public BuildListener listener;
	public AbstractBuild<?, ?> build;
	public int group;
	public String regex;
	public int regexOptions;
	private long interval;
	ArrayList<String> urlSuffixList;
	private static String OS = System.getProperty("os.name").toLowerCase();
	
	
	public BugLogger(HashMap<String, String> params, int source, String hostPort, BuildListener listener,
			AbstractBuild<?, ?> build,String group, String regex, long interval, int regexOptions){
		this.paraMap = params;
		this.source = source;
		this.hostPort = hostPort;
		this.listener = listener;
		this.build = build;
		this.group = Integer.parseInt(group);
		this.regex = regex;
		this.interval = interval;
		this.regexOptions = regexOptions;
	    setFogPrepend();
	    setBuildURL();
		listener.getLogger().println("Starting Bug Logging");
	}
	
	public void runBugLogging() throws FileNotFoundException, UnsupportedEncodingException {
		String content = logon("https://"+fogPrepend+".fogbugz.com/api.asp?cmd=logon&email="+paraMap.get("email")+"&password="+paraMap.get("password"));
		TOKEN = extractString(content, "(.*?CDATA\\[)(.*?)\\].*?",2);
		listener.getLogger().println("Fogbugz Session Token: "+TOKEN);
		setBuildNumber();
		ArrayList<String> events = new ArrayList<String>();
		if(source==1){
			listener.getLogger().println("Source is Console");
			events = EventConsole();
		}else if(source == 2){
			listener.getLogger().println("Source is Console Regex");
			listener.getLogger().println("Regex options chosen: " + (regexOptions>0));
			events = EventRegex();
		}else if(source == 3){
			listener.getLogger().println("Source is Unit");
			events = EventUnit();
		}else if(source == 4){
			listener.getLogger().println("Source is Unit multi");
			events = EventUnitMulti();
		}int count = 0;
		listener.getLogger().println(events.size());
		while(!events.isEmpty()){
			event = events.get(0);
			Boolean submit = false;
			listener.getLogger().println(concatURL());
			try{submit = startHash();}catch(Exception e){}
			if(submit){
				listener.getLogger().println("Now Submitting bug: "+count);
				if(source==4 &&paraMap.get(title).equals("")){
					paraMap.put("title", urlSuffixList.get(0));
					urlSuffixList.remove(0);
				}
				
				submitBug();
			}
			count++;
			events.remove(0);
		}
		
			
		
		logoff("https://"+fogPrepend+".fogbugz.com/api.asp?cmd=logoff&token=");

	}

	public void submitBug() throws UnsupportedEncodingException{
		getHTML(concatURL());
	}

	private String concatURL() throws UnsupportedEncodingException {
		StringBuilder sb = new StringBuilder();
		sb.append("https://"+fogPrepend+".fogbugz.com/api.asp?cmd=new&");
		sb.append(setTitle());
		sb.append(setPriority());
		sb.append(setProject());
		sb.append(setArea());
		sb.append(setAssignedTo());
		sb.append(setEvent());
		sb.append(setComputer());
		sb.append(setVersion());
		sb.append("token="+TOKEN);

		return percentEncode(sb.toString());
	}

	private void logoff(String logoffURL) {
		getHTML(logoffURL+TOKEN);
	}

	public String logon(String logonURL){
		return getHTML(logonURL);
	}

	public String setTitle(){
		return "sTitle="+paraMap.get("title")+"&";
	}

	public String setPriority(){
		return "sPriority=" + paraMap.get("priority")+"&";
	}

	public String setProject(){
		return "sProject="+paraMap.get("project")+"&";
	}

	public String setArea(){
		return "sArea="+paraMap.get("area")+"&";
	}
	public String setEvent(){
		return "sEvent=" + event+"&";
	}

	public String setAssignedTo(){
		return "sPersonAssignedTo=" + paraMap.get("assignedName")+"&";
	}

	public String setComputer(){
		if(paraMap.get("computer") == null)
			return "";
		return "sComputer=" + paraMap.get("computer")+"&";
	}

	public String setOS(){
		return"";
	}

	public String setVersion(){
		if(paraMap.get("version") == null)
			return "";
		return "sVersion=" + paraMap.get("version")+"&";
	}

	@SuppressWarnings("deprecation")
	public ArrayList<String> EventConsole(){
		ArrayList<String> eventList = new ArrayList<String>();
		String s = "";
		try {
			s = build.getLog();
		} catch (Exception e) {e.printStackTrace();}
		extractString(s, "<pre>(.*?)</pre>", 1);
		s=s.replaceAll("<a href.*?>|</a>", "");
		eventList.add(s);
		return eventList;
	}

	@SuppressWarnings("deprecation")
	private ArrayList<String> EventRegex(){
		String s = ""; 
		try {
			s = build.getLog(); 
		} catch (IOException e) {e.printStackTrace();}
		
		return getRegex(s);
	}
	
	public ArrayList<String> EventUnit(){
		String urlPrefix = buildURL+"testReport/";
		String xml = getHTML(urlPrefix);
		ArrayList<String> urlSuffixList = getFailedTests(xml);
		if(urlSuffixList.isEmpty()){
			return EventConsole();
		}
		StringBuilder sb = new StringBuilder();
		for(String url:urlSuffixList){
			xml = getHTML(urlPrefix+url);
			sb.append("Error Message");
			sb.append(":\n" + extractString(xml, "<h\\d>Error Message.*?<pre>(.*?)</pre>", 1));
			sb.append("\n\nStacktrace:\n"+ extractString(xml, "<h\\d>Stacktrace.*?<pre>(.*?)</pre>", 1) + "\n\n\n");
			
		}
		ArrayList<String> eventList = new ArrayList<String>();
		eventList.add(sb.toString());
		return eventList;
	}
	
	public ArrayList<String> EventUnitMulti(){
		String urlPrefix = buildURL+"testReport/";
		String xml = getHTML(urlPrefix);
		urlSuffixList = getFailedTests(xml);
		if(urlSuffixList.isEmpty()){
			return EventConsole();
		}
		ArrayList<String> eventList = new ArrayList<String>();
		StringBuilder sb;
		for(String url:urlSuffixList){
			sb = new StringBuilder();
			xml = getHTML(urlPrefix+url);
			sb.append("Error Message");
			sb.append(":\n" + extractString(xml, "<h\\d>Error Message.*?<pre>(.*?)</pre>", 1));
			sb.append("\n\nStacktrace:\n"+ extractString(xml, "<h\\d>Stacktrace.*?<pre>(.*?)</pre>", 1) + "\n\n\n");
			eventList.add(sb.toString());
		}
		return eventList;
	}
	
	public void setBuildNumber(){
		buildNumber = build.number;
	}
	
	public void setBuildURL(){
		buildURL = "http://"+hostPort+"/"+build.getUrl();
	}
	
	public void setFogPrepend(){
		fogPrepend = paraMap.get("org");
	}

	@SuppressWarnings("deprecation")
	private boolean startHash() throws Exception {
		String hash = hashEvent();
		return searchFile(build.getWorkspace().toString(), "hashFile", hash);	
	}	
	
	public boolean searchFile(String path, String name, String hash) throws Exception{
		Long time = getTime();
		String lineEnd;
		if(isWindows()){
			lineEnd="\r\n";
		}else
			lineEnd="\n";
		File f = new File(path+"\\"+name);
		if(!f.exists()){
			f.createNewFile();
		}
		File temp = new File(path+"\\temp");
		if(!temp.exists()){
			temp.createNewFile();
		}
		FileWriter writer = new FileWriter(temp, true);
		Scanner s = new Scanner(f);
		boolean changed = true;
		
		if(f.length()==0){
			writer.write(hash+" "+time);
			writer.write(lineEnd);
		}else{
			writer.write(hash+" "+time);
			writer.write(lineEnd);
		while(s.hasNextLine()||f.length()==0){
			//listener.getLogger().println("Entering while: "+hash);
			String line = s.nextLine();
			String[] arr = line.split(" ");
			
			if(!compareHash(hash, arr[0])){
				writer.write(arr[0]+" "+arr[1]);
				writer.write(lineEnd);
			}else{
				if(isPastThreshold(time, Long.parseLong(arr[1]), interval*86400000)){
					writer.write(hash+" "+time);
					writer.write(lineEnd);
				}else{
					changed = false;
					writer.write(arr[0]+" "+arr[1]);
					writer.write(lineEnd);
				}
			}
		}
		}
		writer.close();
		s.close();
		if(changed){
			f.delete();
			temp.renameTo(f);
		}else{
			temp.delete();
		}
		return changed;
	}
	
	
	//////////////////Helper Methods//////////////////
	
	
	private boolean compareHash(String hash, String string) {
		return hash.equals(string);
	}
	
	public long getTime(){
		Calendar cal = new GregorianCalendar();
		return cal.getTimeInMillis();
	}
	
	public boolean isPastThreshold(long current, long past, long threshold){
		return past<(current-threshold);
	}
	
	public String getHTML(String urlToRead) {
		URL url;
		HttpURLConnection conn;

		BufferedReader rd;
		String line;
		String result = "";
		try {
			url = new URL(urlToRead);
			conn = (HttpURLConnection) url.openConnection();

			rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			while ((line = rd.readLine()) != null) {
				result += line;
			}
			rd.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	public String extractString(String content, String regex, int group){
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(content);
		String s="";

		if (m.find()){
			s = m.group(group).trim();
		}
		return s;
	}

	public String getXMLString() throws FileNotFoundException{
		String PATH = "";
		String xml = "TEST-TestCases.AsynchronousTestCase.xml";
		PATH = "\\output\\junit-report\\" + xml;
		File file = new File(PATH);
		StringBuilder sb = new StringBuilder();
		Scanner s = new Scanner(file);
		while(s.hasNextLine()){
			sb.append(s.nextLine());
		}s.close();
		String xmlString = sb.toString();
		return xmlString;
	}

	public ArrayList<String> getFailedTests(String xml){
		Pattern r = Pattern.compile("test-.+?'.*?model-link\" href=\"((\\w|/)+)\">\\w",Pattern.DOTALL);
		Matcher m = r.matcher(xml);
		ArrayList<String> ret = new ArrayList<String>();
		while(m.find()){
			String x = m.group(1);
			ret.add(x);
		}
		return ret;
	}
	
	public ArrayList<String> getRegex(String html){
		Pattern r = Pattern.compile(regex,regexOptions);
		Matcher m = r.matcher(html);
		ArrayList<String> ret = new ArrayList<String>();
		while(m.find()){
			String x = m.group(group);
			ret.add(x);
		}
		return ret;
	}

	public String percentEncode(String str) throws UnsupportedEncodingException{
		return URLEncoder.encode(str, "UTF-8");
	}


	public void printHashMap(){
		 Iterator<String> iterator = paraMap.keySet().iterator();  
	       
		    while (iterator.hasNext()) {  
		       String key = iterator.next().toString();  
		       String value = paraMap.get(key).toString();  
		       
		       //listener.getLogger().println(key + " " + value);  
		    }
	}

	public String hashEvent(){
		MessageDigest messageDigest;
		String encrypted = "";
		
		try {
			messageDigest = MessageDigest.getInstance("MD5");
			messageDigest.update(event.getBytes("UTF-8"));
			byte[] byteData= messageDigest.digest();
			StringBuffer sb = new StringBuffer();
			for(int i = 0; i < byteData.length; i++){
				sb.append(Integer.toString((byteData[i] &0xff) +0x100, 16).substring(1));
			}
			encrypted = sb.toString();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return encrypted;
	}
	
	public static boolean isWindows() {
		 
		return (OS.indexOf("win") >= 0);
 
	}
 
	public static boolean isMac() {
 
		return (OS.indexOf("mac") >= 0);
 
	}

}

