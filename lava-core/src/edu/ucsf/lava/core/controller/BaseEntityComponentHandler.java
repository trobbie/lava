package edu.ucsf.lava.core.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DataBinder;
import org.springframework.webflow.context.servlet.ServletExternalContext;
import org.springframework.webflow.definition.FlowDefinition;
import org.springframework.webflow.definition.StateDefinition;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import edu.ucsf.lava.core.action.ActionUtils;
import edu.ucsf.lava.core.action.model.Action;
import edu.ucsf.lava.core.auth.model.AuthUser;
import edu.ucsf.lava.core.dao.file.LavaFile;
import edu.ucsf.lava.core.model.EntityBase;
import edu.ucsf.lava.core.model.LavaEntity;
import edu.ucsf.lava.core.session.CoreSessionUtils;

/*
 * This class serves as the base class for classes that handler entity-based "components" that
 * are included in views.  An entity-based component is one that handles basic CRUD (create,
 * read,update,delete) functionality for a single object (e.g. patient, visit, contact log record).
 * 
 */
public class BaseEntityComponentHandler extends LavaComponentHandler  {
	
	
	public BaseEntityComponentHandler() {
		super();
		// if modify this list, may need to modify InstrumentHandler as well until find time to implement better approach
		defaultEvents = new ArrayList(Arrays.asList(new String[]{"view","edit","cancel","save","add","cancelAdd","applyAdd","saveAdd",
									"close","delete","confirmDelete","cancelDelete","reRender","refresh",
									"custom","custom2","custom3","customEnd","customEnd2","customEnd3"}));
		// set events for which required field validation should be done
		requiredFieldEvents = new ArrayList(Arrays.asList(new String[]{"save", "applyAdd", "saveAdd"}));
		if(getSupportsAttachedFiles()){
			defaultEvents.add("download");
			defaultEvents.add("deleteFile");
			defaultEvents.add("uploadFile");
		}
		
		authEvents = new ArrayList(Arrays.asList(new String[]{"view","edit","add","delete"}));
	}
	
	/*
	 * Shortcut method for use in the constructor of subclasses with only one handled object
	 */
	protected void setHandledEntity(String name, Class clazz){
		Map<String,Class> objectMap = new HashMap<String,Class>();
		objectMap.put(name,clazz);
		this.setHandledObjects(objectMap);
	}

	
	//AUTHORIZATION METHODS
	
	
	//override this if you need to specify a custom LavaEntity for the authroization check
	public Object getEntityForAuthorizationCheck(RequestContext context, Object command){
		return ((ComponentCommand)command).getComponents().get(getDefaultObjectName());
	}
	
	// called upon starting a flow. if authorization fails, the flow terminates after setting
	// an error message to be displayed by the parent flow or if no parent, the error page
	public Event authorizationCheck(RequestContext context, Object command) throws Exception {
		HttpServletRequest request = ((ServletExternalContext)context.getExternalContext()).getRequest();
		
		Object entity = getEntityForAuthorizationCheck(context, command);
		
		//if the component object is not a lava entity, set to null (it cannot reasonably be used in the authorized check below)
		if (entity!=null && !LavaEntity.class.isAssignableFrom(entity.getClass())){
			entity = null;  
		}
		Action action = actionManager.getCurrentAction(request);
		AuthUser user = CoreSessionUtils.getCurrentUser(sessionManager,request);
		// check whether the flow 
		if (isAuthEvent(context)) {
			if (!authManager.isAuthorized(user,action,(LavaEntity)entity)) {
				if (context.getFlowExecutionContext().getActiveSession().isRoot()) {
					throw new RuntimeException(this.getMessage(COMMAND_AUTHORIZATION_ERROR_CODE));
				}else{
					CoreSessionUtils.addFormError(sessionManager, request, new String[]{COMMAND_AUTHORIZATION_ERROR_CODE}, null);
					return new Event(this,this.UNAUTHORIZED_FLOW_EVENT_ID);
				}
			}
		}
		
		return new Event(this,this.SUCCESS_FLOW_EVENT_ID);
	}
	
	public Event initMostRecentViewState(RequestContext context) throws Exception {
		// the most recent view state is used when a view state transitions to a subflow which is 
		// unauthorized such that the subflow is terminated and control returns to the parent flow. the
		// parent flow then uses the "mostRecentViewState" attribute in flow scope to determine which
		// view state to return to. initially, "mostRecentViewState" should be set to the initial
		// view state of the flow, which for entity flows is named after the flow event, aka action, 
		// aka mode, e.g. "view", "edit", "add", "delete", "list", "enter", "collect", etc.
		String initialViewState = ActionUtils.getFlowMode(context.getActiveFlow().getId());
		context.getFlowScope().put("mostRecentViewState", initialViewState);
		return new Event(this,this.SUCCESS_FLOW_EVENT_ID);
	}

	
	//COMMAND OBJECT METHODS
	
	/**
	 * This base method gets the backing object based on the id param.  If this is not
	 * appropriate for the entity type then override in the subclass.  
	 * 
	 * Also, if there are additional objects to load, then override calling the superclass first to load the primary object. 
	 */
	public Map getBackingObjects(RequestContext context, Map components) {
		HttpServletRequest request = ((ServletExternalContext)context.getExternalContext()).getRequest();
		String flowMode = ActionUtils.getFlowMode(context.getActiveFlow().getId()); 
		FlowDefinition flow = context.getActiveFlow();
		StateDefinition state = context.getCurrentState();
		String eventId = ActionUtils.getEventId(context);
		Map backingObjects = new HashMap<String,Object>();
		
		//On add event, create new instance
		if (flowMode.equals("add")) {
			backingObjects.put(getDefaultObjectName(), initializeNewCommandInstance(context,EntityBase.MANAGER.create(getDefaultObjectClass())));
		}else{

			//note: on delete event, there is likely some additional logic that needs to take place...
		
			//get backing object based on id

			// the "id" parameter is stored in flow scope by the flow definition
			String id = context.getFlowScope().getString("id");
			if(StringUtils.isNotEmpty(id)) {
				Object object = EntityBase.MANAGER.getOne(getDefaultObjectBaseClass(), getFilterWithId(request,Long.valueOf(id)));
				if (object != null){
					backingObjects.put(getDefaultObjectName(),object);
				}
				// if entity does not exist, nothing put into backingObjects, and let subclasses decide
				// what to do rather than throwing an exception here
			}else{
				throw new RuntimeException(getMessage("idMissing.command", new Object[]{getDefaultObjectName()}));
			}
		}
		//TODO: need to check for an empty backing objects and redirect to an object not found message. 
		return backingObjects;
	}
	
	//	override in subclasses to provide additional initialization of the new instance.	
	protected Object initializeNewCommandInstance(RequestContext context,Object command){
		return command;
	}

	// this is called when a parent flow resumes after a subflow has completed, so that
	// any changes made in the subflow(s) that may affect the backing objects in the parent
	// flow are reflected
	// note: this is not an event handler. it is a flow action state action (initiated by returning
	// from a subflow), which is why it is a separate method from handleRefreshEvent. also, the
	// FormAction method that calls this always returns success, because once back in the parent
	// flow it is too late to prevent returning from the subflow by returning an error. any exceptions
	// will be displayed as error messages in the parent view.
	public void refreshBackingObjects(RequestContext context, Object command, BindingResult errors) throws Exception {
		// for unknown reasons, calling doRefresh to do a Hibernate refresh here does not work; the changes persisted 
		// in the subflow during this request are not reflected. not sure why. refresh takes detached objects as its 
		// parameter, so in theory it should attach the object to the Hibernate session and refresh it to get the changes.
		// must have something to do with the fact that there are two separate instances of the same object involved, 
		// the subflow instance and the parent flow instance. 
		// re-retrieving the object from the database does get the changes from the object persisted in the subflow. 
		// because the transaction spans the entire request, can see the changes even though they have not been 
		// committed yet, as they are not committed until the request has been processed.
		Map components = ((ComponentCommand)command).getComponents();
		Map backingObjects = this.getBackingObjects(context, components);
		components.putAll(backingObjects);
	}
	
	
    /*
     * The opportunity for this handler to modify the FormAction binder default action. This is the
     * place to add any required fields to the binder.  This is made more complicated by the multiple component 
     * support that we need to have now, so we need to take action to preserve existing required fields
     * specified by other components. 
     */
	 public void initBinder(RequestContext context, Object command, DataBinder binder) {
		 // only set validation for required fields on events where displaying field errors makes sense, i.e. 
		 // an add or save event, as determined by getRequiredFieldEvents
		 // do not want required validation when just refreshing the reference data on a page, e.g. Add Visit,
		 // change projName repopulates visitTypes list
		 String event = ActionUtils.getEventName(context);
		 if (validateRequiredFieldsEvent(context)) {
			String[] requiredFields = defineRequiredFields(context, command);
			if(requiredFields != null && requiredFields.length != 0){
				String[] existingFields = binder.getRequiredFields();
				// convert the required field names into their equivalent bind names within a 
				// ComponentCommand command object, e.g.
				// visitType ==> components['visit'].visitType
				String[] componentRequiredFields = new String[requiredFields.length];
				for (int i=0; i<requiredFields.length;i++) {
					componentRequiredFields[i] = new StringBuffer("components['").append(getDefaultObjectName()).append("'].").append(requiredFields[i]).toString();
				}
				if (existingFields!=null){
					List existing = Arrays.asList(existingFields);
					HashSet fields = new HashSet(existing);
					fields.addAll(Arrays.asList(componentRequiredFields));
					binder.setRequiredFields((String[])fields.toArray());
				}else{
					binder.setRequiredFields(componentRequiredFields);
				}
			}
		 }
	}
		 
		 
	 // override this subclass to set required fields where the request event is needed to 
	 // determine which fields to set as required. if the required fields are not dependent
	 // upon request-specific data, override setRequiredFields instead
	 protected String[] defineRequiredFields(RequestContext context, Object command) {
		 return getRequiredFields();
	 }


	 public void prepareToRender(RequestContext context, Object command, BindingResult errors) {
		 // check the flow id, and if necessary, the event id, to determine how to set componentMode
		 // and componentView for this component
		 HttpServletRequest request =  ((ServletExternalContext)context.getExternalContext()).getRequest();
		 String event = ActionUtils.getEventName(context);
		 String flowMode = ActionUtils.getFlowMode(context.getActiveFlow().getId()); 
		 StateDefinition state = context.getCurrentState();

		if (flowMode.equals("view")) {
			setComponentMode(request, getDefaultObjectName(), "vw");
			setComponentView(request, getDefaultObjectName(), "view");
		}
		else if (flowMode.equals("edit")) {
			setComponentMode(request, getDefaultObjectName(), "dc");
			setComponentView(request, getDefaultObjectName(), "edit");
		}
		else if (flowMode.equals("add")) {
			setComponentMode(request, getDefaultObjectName(), "dc");
			setComponentView(request, getDefaultObjectName(), "add");
		}
		else if (flowMode.equals("delete")) {
			setComponentMode(request, getDefaultObjectName(), "vw");
			setComponentView(request, getDefaultObjectName(), "delete");
		}
	 }

	 
	 public Map addReferenceData(RequestContext context, Object command, BindingResult errors, Map model)
	 {
	 	String flowMode = ActionUtils.getFlowMode(context.getActiveFlow().getId()); 
	 	StateDefinition state = context.getCurrentState();
	 	
	 	super.addReferenceData(context, command, errors, model);
	 	
	 	//add static lists for the entity
	 	this.addListsToModel(model, listManager.getStaticListsForEntity(this.getDefaultObjectName()));
	 	
	 	
	 	
	 	// if entering the "print" view state, need to add the entity as the report dataSource. since
	 	// dataSource must be a Collection or object array, create an array with one object
	 	if (flowMode.equals("view") && state.getId().equals("print")) {
	 		model.put("entityReportDataSource", new Object[] {((ComponentCommand)command).getComponents().get(getDefaultObjectName())});
	 		model.put("format", "pdf");
	 		// pass the handler itself as a report parameter
	 		model.put("handler", this);
	 	}
	 		
	 	return model;
	 }
	 
	 
	 public Event handleFlowEvent(RequestContext context, Object command, BindingResult errors) throws Exception {
		String eventName = ActionUtils.getEventName(context);
		// note: do not use "eventName = ActionUtils.getEvent(request)" because it is possible for a flow
		//  to set an overrideEvent which will be different from the event request parameter
		
		if(eventName.equals("cancel")){
			return this.handleCancelEvent(context,command,errors);
		}
		else if(eventName.equals("save")){
			return this.handleSaveEvent(context,command,errors);
		}	
		else if(eventName.equals("add")){
			return this.handleAddEvent(context,command,errors);
		}
		else if(eventName.equals("saveAdd") || eventName.equals("applyAdd")){
			return this.handleSaveAddEvent(context,command,errors);
		}	
		else if(eventName.equals("cancelAdd")){
			return this.handleCancelAddEvent(context,command,errors);
		}	
		else if(eventName.equals("edit")){
			return this.handleEditEvent(context,command,errors);
		}	
		else if(eventName.equals("close")){
			return this.handleCloseEvent(context,command,errors);
		}	
		else if(eventName.equals("delete")){
			return this.handleDeleteEvent(context,command,errors);
		}	
		else if(eventName.equals("confirmDelete")){
			return this.handleConfirmDeleteEvent(context,command,errors);
		}
		else if(eventName.equals("cancelDelete")){
			return this.handleCancelDeleteEvent(context,command,errors);
		}
		else if(eventName.equals("reRender")){
			return this.handleReRenderEvent(context,command,errors);
		}
		else if(eventName.equals("refresh")){
			return this.handleRefreshEvent(context,command,errors);
		}
		else if(eventName.equals("deleteFile")){
			return this.handleDeleteFileEvent(context, command, errors);
		}
		else if(eventName.equals("uploadFile")){
			return this.handleUploadFileEvent(context,command,errors);
		}
		else {
			return this.handleCustomEvent(context,command,errors);
		}
	}


	
//CUSTOM EVENT METHOD
	
	//override this in subclass to handle custom action buttons. 
	protected Event handleCustomEvent(RequestContext context, Object command, BindingResult errors) throws Exception{
		return new Event(this,SUCCESS_FLOW_EVENT_ID);
	}

	
//VIEW EVENT METHODS
	public Event handleViewEvent(RequestContext context, Object command, BindingResult errors) throws Exception{
		return doView(context, command,errors);
	}
	
	//Override this in subclass to provide custom "view" handler 
	protected Event doView(RequestContext context, Object command, BindingResult errors) throws Exception{
		return new Event(this,SUCCESS_FLOW_EVENT_ID);
	}
	

//CLOSE EVENT  METHODS		
	public Event handleCloseEvent(RequestContext context, Object command, BindingResult errors) throws Exception {
		return doClose(context, command,errors);
	}
		
	//Override this in subclass to provide custom close handler 
	protected Event doClose(RequestContext context, Object command, BindingResult errors) throws Exception {
		return new Event(this,SUCCESS_FLOW_EVENT_ID);
	}
		
			
//EDIT EVENT METHODS
	public Event handleEditEvent(RequestContext context, Object command, BindingResult errors) throws Exception{
		return doEdit(context,command,errors);
	}
	
	//Override this in subclass to provide custom edit handler 
	protected Event doEdit(RequestContext context, Object command, BindingResult errors) throws Exception{
		return new Event(this,SUCCESS_FLOW_EVENT_ID);
	}


//	CANCEL EVENT  METHODS		
	public Event handleCancelEvent(RequestContext context, Object command, BindingResult errors) throws Exception{
		return doCancel(context,command,errors);
	}
	
	//Override this in subclass to provide custom close handler 
	protected Event doCancel(RequestContext context, Object command, BindingResult errors) throws Exception{
		Map components = ((ComponentCommand)command).getComponents();
		return refreshHandledObjects(context, components, errors);
	}

	// loops through the command map and refreshes the command objects handled by this
	// note: lists and views invoke FormAction refreshFormObject (which calls handler refreshBackingObjects)
	// instead of using this refresh techique. see comments in refreshBackingObjects
	protected Event refreshHandledObjects(RequestContext context, Map objects, BindingResult errors) {
		Event returnEvent = new Event(this,SUCCESS_FLOW_EVENT_ID);
		try {
			for(String objectName : handledObjects.keySet())
			{
				LavaEntity object = (LavaEntity) objects.get(objectName);
				object.refresh();
			}
		}					
		catch (Exception e) {
			addObjectErrorForException(errors, e);
			returnEvent = new Event(this,ERROR_FLOW_EVENT_ID);
		}
		return returnEvent;
	}
	

//SAVE EVENT METHODS
	public Event handleSaveEvent(RequestContext context, Object command, BindingResult errors) throws Exception{
		return doSave(context,command,errors);
	}
	//The default save action handler 
	protected Event doSave(RequestContext context, Object command, BindingResult errors) throws Exception{
		Map components = ((ComponentCommand)command).getComponents();
		Event returnEvent = saveHandledObjects(context, components, errors);
		if (returnEvent.getId().equals(SUCCESS_FLOW_EVENT_ID)) {
			// do refresh in case any db triggers populated fields on save. this is just for
			// entities that need to be refreshed between states of the same flow, e.g. the 
			// instrument enter and collect flows. in the case where an entity is being saved 
			// and the flow is ending, this refresh is irrelevant (but does not hurt) because 
			// it is refreshing the entity in the current flow scope, and if the entity also 
			// exists in a parent flow scope, it will need to be refreshed there by a different 
			// mechanism. that mechanism is the subFlowReturnState, which refreshes all components 
			// of its flow when it resumes after a subFlow completes
			returnEvent = refreshHandledObjects(context, components, errors);
		}		
		return returnEvent;
	}

	//loops through the command map and saves the command objects handled by this handler
	protected Event saveHandledObjects(RequestContext context, Map objects, BindingResult errors){
		HttpServletRequest request = ((ServletExternalContext)context.getExternalContext()).getRequest();
		Event returnEvent = new Event(this,SUCCESS_FLOW_EVENT_ID);
		try {
			for(String objectName : handledObjects.keySet())
			{
				// entities which have internal validation logic checks should override
				// validate and throw a RuntimeException if validation fails
				// additionally, any conditional required field validation
				//TODO: look into replacing this technique with Validator objects
				LavaEntity object = (LavaEntity)objects.get(objectName);
				object.validate(metadataManager);
				object.save();
			}
		}					
		catch (Exception e) {
			addObjectErrorForException(errors, e);
			returnEvent = new Event(this,ERROR_FLOW_EVENT_ID);
		}
		return returnEvent;
	}
		
//	ADD EVENT METHODS
	public Event handleAddEvent(RequestContext context, Object command, BindingResult errors) throws Exception{
		return doAdd(context,command,errors);
	}
	
	//Override this in subclass to provide custom add (pre-add) handler 
	protected Event doAdd(RequestContext context, Object command, BindingResult errors) throws Exception{
		return new Event(this,SUCCESS_FLOW_EVENT_ID);
	}
		
//	SAVE ADD EVENT METHODS (and APPLY ADD EVENT)
	public Event handleSaveAddEvent(RequestContext context, Object command, BindingResult errors) throws Exception{
		return doSaveAdd(context,command,errors);
	}
	
	//Override this in subclass to provide custom add (on save) handler 
	protected Event doSaveAdd(RequestContext context, Object command, BindingResult errors) throws Exception{
		Map components = ((ComponentCommand)command).getComponents();
		Event returnEvent = saveHandledObjects(context, components, errors);
		if (returnEvent.getId().equals(SUCCESS_FLOW_EVENT_ID)) {
			// do refresh in case any db triggers populated fields on saveAdd
			returnEvent = refreshHandledObjects(context, components, errors);
		}
		return returnEvent;
	}
		
//	CANCEL ADD EVENT  METHODS		
	public Event handleCancelAddEvent(RequestContext context, Object command, BindingResult errors) throws Exception{
		return doCancelAdd(context,command,errors);
	}
	
	//Override this in subclass to provide custom cancel add handler 
	protected Event doCancelAdd(RequestContext context, Object command, BindingResult errors) throws Exception{
		//TODO: is there a "recycle" command on hibernate to use to dispose of the entity;
		return new Event(this,SUCCESS_FLOW_EVENT_ID);
	}
		
//	DELETE EVENT METHODS
	public Event handleDeleteEvent(RequestContext context, Object command, BindingResult errors) throws Exception{
		return doDelete(context,command,errors);
	}
	
	//Override this in subclass to provide custom delete (pre-confirm) handler 
	protected Event doDelete(RequestContext context, Object command, BindingResult errors) throws Exception{
		return new Event(this,SUCCESS_FLOW_EVENT_ID);
	}
		
		
//CONFIRM DELETE METHODS
	public Event handleConfirmDeleteEvent(RequestContext context, Object command, BindingResult errors) throws Exception{
		return doConfirmDelete(context,command,errors);
	}
	
	//Override this in subclass to provide custom delete handler 
	protected Event doConfirmDelete(RequestContext context, Object command, BindingResult errors) throws Exception{
		Map components = ((ComponentCommand)command).getComponents();
		return deleteHandledObjects(context, components, errors);
	}

	//loops through the command map and deletes the command objects handled by this handler
	protected Event deleteHandledObjects(RequestContext context, Map objects, BindingResult errors){
		Event returnEvent = new Event(this,SUCCESS_FLOW_EVENT_ID);
		try {
			LavaEntity object;
			for(String objectName : handledObjects.keySet())
			{
				object = (LavaEntity)objects.get(objectName);
				objects.remove(object);
				object.delete();
			}
			
			//delete any attached files as well
			if(this.supportsAttachedFiles()){
				deleteHandledFile(context, objects, errors);
			}
		}
		catch (Exception e) {
			addObjectErrorForException(errors, e);
			returnEvent = new Event(this,ERROR_FLOW_EVENT_ID);
		}
		return returnEvent;
	}

//CANCEL DELETE METHODS
	public Event handleCancelDeleteEvent(RequestContext context, Object command, BindingResult errors) throws Exception{
		return doCancelDelete(context,command,errors);
	}
	
	//Override this in subclass to provide custom cancel delete  handler 
	protected Event doCancelDelete(RequestContext context, Object command, BindingResult errors) throws Exception{
		return new Event(this,SUCCESS_FLOW_EVENT_ID);
	}

// RERENDER EVENT METHOD
	// render the page again, when user has changed a value upon which other values depend.
	// in the common case, this event does not require a handle event method, because all that is required is 
	// data binding in conjunction with the flow's view-state render actions, setupForm and prepareToRender 
	// (which calls referenceData to update dynamic lists)
	public Event handleReRenderEvent(RequestContext context, Object command, BindingResult errors) throws Exception {
		return doReRender(context,command,errors);
	}

	//Override this in subclass to provide custom "reRender" handler 
	protected Event doReRender(RequestContext context, Object command, BindingResult errors) throws Exception {
		return new Event(this,SUCCESS_FLOW_EVENT_ID);
	}

	
//REFRESH EVENT METHOD
	// refresh the command object in the current handler from the database.
	// note that this is also done inside of doSave and doSaveAdd so that any updates via database
	// triggers are reflected in the command object
	public Event handleRefreshEvent(RequestContext context, Object command, BindingResult errors) throws Exception {
		return doRefresh(context,command,errors);
	}

	//Override this in subclass to provide custom "view" handler 
	protected Event doRefresh(RequestContext context, Object command, BindingResult errors) throws Exception {
		return refreshHandledObjects(context, ((ComponentCommand)command).getComponents(), errors);
	}

	
	//DELETE FILE
	public Event handleDeleteFileEvent(RequestContext context, Object command, BindingResult errors) throws Exception {
			return this.doDeleteFile(context, command, errors);
		}
	
	public Event deleteHandledFile(RequestContext context, Map components, BindingResult errors) throws Exception{
		if(this.getFileDao()==null) {new Event(this,ERROR_FLOW_EVENT_ID);}
		this.getFileDao().deleteFile(getDownloadFileId(components.get(getDefaultObjectName())));
		return new Event(this,SUCCESS_FLOW_EVENT_ID);
	}

	public Event doDeleteFile(RequestContext context, Object command, BindingResult errors) throws Exception {
				return deleteHandledFile(context, ((ComponentCommand)command).getComponents(), errors);
		}
		
	//UPLOAD FILE
	public Event handleUploadFileEvent(RequestContext context, Object command, BindingResult errors) throws Exception {
			return this.doDeleteFile(context, command, errors);
		}
	public Event doUploadFile(RequestContext context, Object command, BindingResult errors) throws Exception {
		if(this.getFileDao()==null) {new Event(this,ERROR_FLOW_EVENT_ID);}
			this.getFileDao().storeFile(getUploadFile(context,command, errors));
		return new Event(this,SUCCESS_FLOW_EVENT_ID);
		}
	
	protected LavaFile getUploadFile(RequestContext context, Object command, BindingResult errors){
		return new LavaFile(); 
	}
	
		
	
}