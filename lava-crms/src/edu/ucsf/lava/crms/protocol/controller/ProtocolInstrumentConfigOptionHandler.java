package edu.ucsf.lava.crms.protocol.controller;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.validation.BindingResult;
import org.springframework.webflow.context.servlet.ServletExternalContext;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import edu.ucsf.lava.core.controller.ComponentCommand;
import edu.ucsf.lava.core.model.EntityBase;
import edu.ucsf.lava.crms.controller.CrmsEntityComponentHandler;
import edu.ucsf.lava.crms.protocol.model.ProtocolInstrumentConfig;
import edu.ucsf.lava.crms.protocol.model.ProtocolInstrumentConfigOption;
import edu.ucsf.lava.crms.protocol.model.ProtocolVisitConfig;

public class ProtocolInstrumentConfigOptionHandler extends CrmsEntityComponentHandler {

	public ProtocolInstrumentConfigOptionHandler() {
		super();
		setHandledEntity("protocolInstrumentConfigOption", ProtocolInstrumentConfigOption.class);
	}

	protected String[] defineRequiredFields(RequestContext context, Object command) {
		// for now, not using label for options
	    setRequiredFields(new String[]{"instrType"});
	    return getRequiredFields();
	}
	
	protected Object initializeNewCommandInstance(RequestContext context, Object command){
		HttpServletRequest request = ((ServletExternalContext)context.getExternalContext()).getRequest();
		
		ProtocolInstrumentConfigOption instrumentOptionConfig = (ProtocolInstrumentConfigOption) command;
		ProtocolInstrumentConfig instrumentConfig = (ProtocolInstrumentConfig) ProtocolInstrumentConfig.MANAGER.getOne(EntityBase.newFilterInstance().addIdDaoEqualityParam(Long.valueOf(context.getFlowScope().getString("param"))));
		// the collection association between ProtocolInstrumentConfig and ProtocolInstrumentOptionConfig need only be unidirectional
		// but was made bidirectional so that ProtocolInstrumentConfig does not have to be persisted in this handler (which 
		// would require doSaveAdd retrieving ProtocolInstrumentConfig again to persist it). ProtocolInstrumentConfig
		// addOption manages both ends of the association, but only have to persist the collection end, ProtocolInstrumentOptionConfig
		instrumentConfig.addOption(instrumentOptionConfig);
		instrumentOptionConfig.setProjName(instrumentConfig.getProjName());
		return command;
	}
	
}
