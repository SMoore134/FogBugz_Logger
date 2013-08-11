package org.jenkinsci.plugins.sem2458;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import org.jenkinsci.plugins.StephenMoore.BugLogger;
/* 
 * @author Stephen Moore
 */
public class FogBugzPublisher extends Publisher{

    private String HostPort;
    private String FogBugzParameters;
    private boolean console;
    private boolean regex;
    private boolean unit;
    private boolean multi;
	private String Group;
	private String RegexField;
	private String Interval; 
	private boolean CaseInsensitive;
	private boolean DotAll;
	private boolean CanonEq;
	private boolean Comments;
	private boolean Literal;
	private boolean Multiline;
	private boolean UnicodeCase;
	private boolean UnicodeCharacterClass;
	private boolean UnixLines;
	
    @SuppressWarnings("deprecation")
	@DataBoundConstructor
    public FogBugzPublisher(String HostPort, String FogBugzParameters, boolean console, boolean regex, boolean unit, boolean multi, String Group, String RegexField, String Interval, boolean CaseInsensitive, boolean DotAll, boolean CanonEq, boolean Comments, boolean Literal, boolean Multiline, boolean UnicodeCase, boolean UnicodeCharacterClass, boolean UnixLines /*,boolean regexOptions, boolean Advanced*/) {
    	this.HostPort = HostPort;
    	this.FogBugzParameters = FogBugzParameters;
    	this.console = console;
    	this.regex = regex;
    	this.unit = unit;
    	this.multi = multi;
    	this.Group = Group;
    	this.RegexField = RegexField;
    	this.Interval = Interval; 
    	this.CaseInsensitive = CaseInsensitive;
    	this.DotAll = DotAll;
    	this.CanonEq = CanonEq;
    	this.Comments = Comments;
    	this.Literal = Literal;
    	this.Multiline = Multiline;
    	this.UnicodeCase = UnicodeCase;
    	this.UnicodeCharacterClass = UnicodeCharacterClass;
    	this.UnixLines = UnixLines;
    	
    }


	public String getHostPort() {
        return HostPort;
	}
	
	public String getFogBugzParameters() {
        return FogBugzParameters;
	}
	
	public boolean isConsole(){
		return console;
	}
	
	public boolean isRegex(){
		return regex;
	}
	
	public String getGroup() {
        return Group;
	}
	
	public String getRegexField() {
        return RegexField;
	}
	
	public boolean isUnit(){
		return unit;
	}
	
	public boolean isMulti(){
		return multi;
	}
	
	public boolean isCanonEq(){
		return CanonEq;
	}
	public boolean isCaseInsensitive(){
		return CaseInsensitive;
	}
	public boolean isComments(){
		return Comments;
	}
	public String getInterval(){
		return Interval;
	}
	public boolean isDotAll(){
		return DotAll;
	}
	public boolean isLiteral(){
		return Literal;
	}
	public boolean isMultiline(){
		return Multiline;
	}
	public boolean isUnicodeCase(){
		return UnicodeCase;
	}
	public boolean isUnicodeCharacterClass(){
		return UnicodeCharacterClass;
	}
	public boolean isUnixLines(){
		return UnixLines;
	}
	
	public int RegexMask(){
		int result = 0;
		if(CanonEq)
			result = result|128;
		if(CaseInsensitive)
			result = result|2;
		if(Comments)
			result = result|4;
		if(DotAll)
			result = result|32;
		if(Literal)
			result = result|16;
		if(Multiline)
			result = result|8;
		if(UnicodeCase)
			result = result|64;
		if(UnicodeCharacterClass)
			result = result|256;
		if(UnixLines)
			result = result|1;
		return result;
	}
	private boolean isResultGood(AbstractBuild<?, ?> build) {
        Result result = build.getResult();
        if (result == null) {
            return true;
        }
        return build.getResult().isBetterThan(Result.UNSTABLE);
    }
    
	private HashMap<String, String> paramsToMap(){
		HashMap<String, String> paraMap = new HashMap<String, String>();
		String[] paramArray = FogBugzParameters.split("\" \"");
		for(String x: paramArray){
			x = x.replaceAll("\"", "");
			String[] temp = x.split("=");
			paraMap.put(temp[0], temp[1]);
		}		
		return paraMap;
	}
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws FileNotFoundException, UnsupportedEncodingException {
    	if(!isResultGood(build)){
    		listener.getLogger().println();
    		int InfoFrom = 0;
    		if(console)
    			InfoFrom = 1;
    		else if(regex)
    			InfoFrom = 2;
    		else if(unit)
    			InfoFrom = 3;
    		else if(multi)
    			InfoFrom = 4;
    		BugLogger logger = new BugLogger(paramsToMap(), InfoFrom, HostPort, listener, build, Group, RegexField,Long.parseLong(Interval), RegexMask());
    		logger.runBugLogging();
    	}else{
    		listener.getLogger().println("Because build was a success, no bugs will be logged.");
    	}
    		return true;
    }

    
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

   
    @Extension 
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
       

       
        public FormValidation doCheckHostPort(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set jenkins host:port");
            if (!value.matches("\\w+:\\w+"))
                return FormValidation.warning("Not a valid form, Are you sure about this?");
            return FormValidation.ok();
        }
		
		public FormValidation doCheckFogBugzParameters(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Empty Parameter list");
            if (!value.matches("\"(.*?=.*?\" \")+.*?=.*?\""))
                return FormValidation.warning("Not a valid form, Are you sure about this?");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
           
            return true;
        }

       
        public String getDisplayName() {
            return "[FogBugz Logger] - Auto-log bugs in FogBugz";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
         
            save();
            return super.configure(req,formData);
        }

        
    }

	public BuildStepMonitor getRequiredMonitorService() {
		
		return BuildStepMonitor.NONE;
	}
}