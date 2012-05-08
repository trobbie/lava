package edu.ucsf.lava.crms.protocol.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.webflow.context.servlet.ServletExternalContext;
import org.springframework.webflow.execution.RequestContext;

import edu.ucsf.lava.core.dao.LavaDaoFilter;
import edu.ucsf.lava.crms.session.CrmsSessionUtils;

public class PatientDeviationStatusHandler extends BaseDeviationStatusHandler {

	public PatientDeviationStatusHandler() {
		super();
		this.setHandledList("patientDeviationStatus","protocolStatusList");
	}
	
	public LavaDaoFilter extractFilterFromRequest(RequestContext context, Map components) {
		LavaDaoFilter filter = super.extractFilterFromRequest(context, components);
		HttpServletRequest request =  ((ServletExternalContext)context.getExternalContext()).getRequest();
		return CrmsSessionUtils.setFilterPatientContext(sessionManager,request,filter);
	}
	
}
