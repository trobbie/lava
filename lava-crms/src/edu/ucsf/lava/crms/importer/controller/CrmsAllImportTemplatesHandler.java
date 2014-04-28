package edu.ucsf.lava.crms.importer.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.webflow.context.servlet.ServletExternalContext;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import edu.ucsf.lava.core.dao.LavaDaoFilter;
import edu.ucsf.lava.core.importer.controller.AllImportTemplatesHandler;
import edu.ucsf.lava.crms.importer.model.CrmsImportTemplate;
import edu.ucsf.lava.crms.session.CrmsSessionUtils;

public class CrmsAllImportTemplatesHandler extends AllImportTemplatesHandler {
	
	public CrmsAllImportTemplatesHandler() {
		super();
//PROB DO NOT NEED THIS SINCE SAME AS SUPERCLASS		
		this.setHandledList("allImportTemplates","importTemplate");
		this.setEntityForStandardSourceProvider(CrmsImportTemplate.class);
	}
	
	/**
	 * The idea here is that if this is a crms application, then we always want
	 * to use the CrmsAllImportTemplatesHandler instead of the core AllImportTemplatesHandler.  If scopes
	 * need to extend ImportTemplate further, then they should subclass and customize this
	 * handler/action. 
	 */
	@Override
	public Event preSetupFlowDirector(RequestContext context) throws Exception {
		return new Event(this,CONTINUE_FLOW_EVENT_ID);
	}
		

//MAY NOT NEED AS SAME AS SUPERCLASS NOW BUT MAY ADD crms SPECIFIC THINGS	
	public LavaDaoFilter extractFilterFromRequest(RequestContext context, Map components) {
		HttpServletRequest request =  ((ServletExternalContext)context.getExternalContext()).getRequest();
		LavaDaoFilter filter =  CrmsSessionUtils.setFilterProjectContext(sessionManager,request,CrmsImportTemplate.newFilterInstance(getCurrentUser(request)));
		filter.addDefaultSort("name",true);
		return filter;
	}
	
	public void updateFilterFromContext(LavaDaoFilter filter, RequestContext context, Map components){
		HttpServletRequest request =  ((ServletExternalContext)context.getExternalContext()).getRequest();
		// note that the projName might be null if it is supplied in the data file
		CrmsSessionUtils.setFilterProjectContext(sessionManager,request,filter);
	}

}
