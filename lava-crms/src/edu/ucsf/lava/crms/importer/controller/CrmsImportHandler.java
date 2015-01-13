package edu.ucsf.lava.crms.importer.controller;

import static edu.ucsf.lava.core.importer.model.ImportDefinition.CSV_FORMAT;
import static edu.ucsf.lava.core.importer.model.ImportDefinition.DEFAULT_DATE_FORMAT;
import static edu.ucsf.lava.core.importer.model.ImportDefinition.DEFAULT_TIME_FORMAT;
import static edu.ucsf.lava.core.importer.model.ImportDefinition.TAB_FORMAT;
import static edu.ucsf.lava.crms.importer.model.CrmsImportDefinition.MAY_OR_MAY_NOT_EXIST;
import static edu.ucsf.lava.crms.importer.model.CrmsImportDefinition.MUST_EXIST;
import static edu.ucsf.lava.crms.importer.model.CrmsImportDefinition.MUST_NOT_EXIST;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.sql.Time;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.webflow.context.servlet.ServletExternalContext;
import org.springframework.webflow.core.collection.AttributeMap;
import org.springframework.webflow.core.collection.LocalAttributeMap;
import org.springframework.webflow.definition.StateDefinition;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import au.com.bytecode.opencsv.CSVReader;
import edu.ucsf.lava.core.controller.ComponentCommand;
import edu.ucsf.lava.core.controller.LavaComponentFormAction;
import edu.ucsf.lava.core.dao.LavaDaoFilter;
import edu.ucsf.lava.core.file.model.ImportFile;
import edu.ucsf.lava.core.importer.controller.ImportHandler;
import edu.ucsf.lava.core.importer.model.ImportDefinition;
import edu.ucsf.lava.core.importer.model.ImportLog;
import edu.ucsf.lava.core.importer.model.ImportSetup;
import edu.ucsf.lava.core.manager.Managers;
import edu.ucsf.lava.core.model.EntityBase;
import edu.ucsf.lava.core.model.LavaEntity;
import edu.ucsf.lava.core.session.CoreSessionUtils;
import edu.ucsf.lava.core.type.LavaDateUtils;
import edu.ucsf.lava.crms.assessment.InstrumentManager;
import edu.ucsf.lava.crms.assessment.model.Instrument;
import edu.ucsf.lava.crms.auth.CrmsAuthUtils;
import edu.ucsf.lava.crms.enrollment.EnrollmentManager;
import edu.ucsf.lava.crms.enrollment.model.EnrollmentStatus;
import edu.ucsf.lava.crms.importer.model.CrmsImportDefinition;
import edu.ucsf.lava.crms.importer.model.CrmsImportLog;
import edu.ucsf.lava.crms.importer.model.CrmsImportSetup;
import edu.ucsf.lava.crms.manager.CrmsManagerUtils;
import edu.ucsf.lava.crms.people.model.Caregiver;
import edu.ucsf.lava.crms.people.model.ContactInfo;
import edu.ucsf.lava.crms.people.model.Patient;
import edu.ucsf.lava.crms.project.ProjectManager;
import edu.ucsf.lava.crms.scheduling.VisitManager;
import edu.ucsf.lava.crms.scheduling.model.Visit;
import edu.ucsf.lava.crms.session.CrmsSessionUtils;


/**
 * CrmsImportHandler
 * 
 * Handles the crms specific part of importing a data file.
 * 
 * @author ctoohey
 *
 */
public class CrmsImportHandler extends ImportHandler {
	protected InstrumentManager instrumentManager;
	protected EnrollmentManager enrollmentManager;
	protected VisitManager visitManager; 
	protected ProjectManager projectManager; 
	
	public CrmsImportHandler() {
		super();
		// the defaultObjectName should ideally be the same as the target part of the action which
		// uses this handler, i.e. lava.core.importer.import.import so target='import', because
		// the flow constructs event transitions using the target part of the action (at least for
		// customizing actions) while the decorator uses the defaultObjectName on eventButton that
		// will construct the event to be submitted which should match the transition
		setHandledEntity("import", CrmsImportSetup.class);
		setDefaultObjectBaseClass(ImportSetup.class);
	}
	
	public void updateManagers(Managers managers){
		super.updateManagers(managers);
		this.enrollmentManager = CrmsManagerUtils.getEnrollmentManager(managers);
		this.instrumentManager = CrmsManagerUtils.getInstrumentManager(managers);
		this.projectManager = CrmsManagerUtils.getProjectManager(managers);
		this.visitManager = CrmsManagerUtils.getVisitManager(managers);
	}

	/**
	 * The idea here is that if this is a crms application, then we always want
	 * to use the CrmsImportHandler instead of the core ImportHandler.  If scopes
	 * need to extend Import further, then they should subclass and customize this
	 * handler/action. 
	 */
	@Override
	public Event preSetupFlowDirector(RequestContext context) throws Exception {
		return new Event(this,CONTINUE_FLOW_EVENT_ID);
	}
	

	
	
	
//TODO: *** implement isAuthorized for project authorization (make sure proj auth flag is 
	// set on importSetup. assume it is ok that importSetup is not a persistent object
	
	
	
	public Map getBackingObjects(RequestContext context, Map components) {
		Map backingObjects = super.getBackingObjects(context, components);
		CrmsImportSetup importSetup = (CrmsImportSetup) components.get(this.getDefaultObjectName());
		
		// replace the importLog for crms
		ImportLog baseImportLog = (ImportLog) backingObjects.get("importLog");
		CrmsImportLog importLog = new CrmsImportLog(baseImportLog);
		backingObjects.put("importLog", importLog);
		
		return backingObjects;
	}
	
	
	protected Event doImport(RequestContext context, Object command, BindingResult errors) throws Exception {
		CrmsImportSetup importSetup = (CrmsImportSetup) ((ComponentCommand)command).getComponents().get(this.getDefaultObjectName());
		CrmsImportLog importLog = (CrmsImportLog) ((ComponentCommand)command).getComponents().get("importLog");
		Event returnEvent = new Event(this,this.SUCCESS_FLOW_EVENT_ID);
		Event handlingEvent = null;

		// the CrmsImportSetup command object is used as a parameter object to pass parameters to methods which would
		// otherwise require many arguments
		// additionally it facilitates using properties from its ImportSetup superclass in this handler 
		// these include the columns array (mappingCols) and properties arrays (mappingEntities, mappingProps) 
		// that ImportHandler creates when reading the definition mapping file
		if ((returnEvent = super.doImport(context, command, errors)).getId().equals(SUCCESS_FLOW_EVENT_ID)) {
			CrmsImportDefinition importDefinition = (CrmsImportDefinition) importSetup.getImportDefinition();

			importLog.setProjName(importDefinition.getProjName());
			importLog.setNotes(importSetup.getNotes());

			// read data file
// NOTE: remember to review jfesenko data load script		
			ImportFile dataFile = importLog.getDataFile();
			int lineNum = 0;
			InputStream dataFileContent = new ByteArrayInputStream(dataFile.getContent());
			// open data file contents with a CSVReader for parsing CSV values, accounting for things like
			// quoted strings that contain comments, etc.
			CSVReader reader = null;
			if (importSetup.getImportDefinition().getDataFileFormat().equals(CSV_FORMAT)) {
				 reader = new CSVReader(new InputStreamReader(dataFileContent));
			}
			else if (importSetup.getImportDefinition().getDataFileFormat().equals(TAB_FORMAT)) {
				 reader = new CSVReader(new InputStreamReader(dataFileContent), '\t');
			}
			// nextLine[] is an array of values from the line
			String [] nextLine;
				
			// opencsv readNext parses the record into a String array
			while ((nextLine = reader.readNext()) != null) {
				lineNum++;
					
				// number of lines < MAX_LINES
				//if (++lineNum > MAX_LINES) {
				//	break;
				//}
						
				// skip over the data file column headers line (it has already been read into the importSetup
				// dataCols by the superclass)
//TODO: need to test this (believe that BASC export file has 2 header rows so that would be a good test)		
				int startLine = importDefinition.getStartDataRow() != null ? importDefinition.getStartDataRow() : 2;
				if (lineNum < startLine) {
					continue;
				}
				
				importSetup.reset(); // reset created/existed flags to false, entities that are retrieved or created to null
				
				// note that indices of data array items in data file match up with indices of column and 
				// property array items in import definition mapping file
				importSetup.setDataValues(nextLine);

				// skip over blank lines. check first couple cols
				if (!StringUtils.hasText(importSetup.getDataValues()[0]) && !StringUtils.hasText(importSetup.getDataValues()[1]) && !StringUtils.hasText(importSetup.getDataValues()[2])) {
					continue;
				}

				importLog.incTotalRecords(); // includes records that cannot be exported due to some error

				// allow subclasses to custom generate revisedProjName (e.g. append unit/site to projName), which
				// is used everywhere a projName is needed in the import
				// this needs to be called for each record because site could differ for each record
				generateRevisedProjName(importDefinition, importSetup);
	
				// find existing Patient. possibly create new Patient
				if ((handlingEvent = patientExistsHandling(context, errors, importDefinition, importSetup, importLog, lineNum)).getId().equals(ERROR_FLOW_EVENT_ID)) {
					this.updateEntityCounts(importSetup, importLog);
					continue;
				}

				// if no errors, continue processing import record. importSetup patientCreated indicates whether a
				// new Patient record was created or an existing Patient record was found (and given no errors, this 
				// means that all import definition flags were successfully met such that the record can be imported
				// with either a new or existing Patient)
				// (this goes for Status, Visit and instrument as well)

				// if Patient MUST_EXIST then importing assessment data, so do not deal with creating ContactInfo
				if (!importDefinition.getPatientExistRule().equals(MUST_EXIST)) {
					if ((handlingEvent = contactInfoExistsHandling(context, errors, importDefinition, importSetup, importLog, lineNum)).getId().equals(ERROR_FLOW_EVENT_ID)) {
						this.updateEntityCounts(importSetup, importLog);
						continue;
					}
				}
				
				// because caregiverExistsHandling may be reused for multiple Caregiver instances if data file has multiple Caregivers, it does not
				// directly set entities on importSetup like other existsHandling methods; instead it passes flags and instantiated entities back 
				// via the returned Event, which has attributes, and the values of these attributes are then use to set the Caregiver.
				
				// note that Caregiver and Caregiver ContactInfo are tightly bound such that both are handled together since there
				// is an assumption that Caregiver ContactInfo would never be imported without importing Caregiver data (i.e. would 
				// never import new ContactInfo record for an existing Caregiver). so if a Caregiver is created a Caregiver ContactInfo
				// might also be created (if there is data in the import data file)
				if ((handlingEvent = caregiverExistsHandling(context, errors, importDefinition, importSetup, importLog, 
						importSetup.getIndexCaregiverFirstName(), importSetup.getIndexCaregiverLastName(), 
						importSetup.getIndexCaregiverContactInfoAddress(), importSetup.getIndexCaregiverContactInfoCity(),
						importSetup.getIndexCaregiverContactInfoState(), importSetup.getIndexCaregiverContactInfoZip(),
						importSetup.getIndexCaregiverContactInfoPhone1(), importSetup.getIndexCaregiverContactInfoEmail(),
						lineNum)).getId().equals(ERROR_FLOW_EVENT_ID)) {
					this.updateEntityCounts(importSetup, importLog);
					continue;
				}
				importSetup.setCaregiverCreated((Boolean) handlingEvent.getAttributes().get("caregiverCreated"));
				importSetup.setCaregiverExisted((Boolean) handlingEvent.getAttributes().get("caregiverExisted"));
				if (importSetup.isCaregiverCreated() || importSetup.isCaregiverExisted()) {
					importSetup.setCaregiver((Caregiver) handlingEvent.getAttributes().get("caregiver"));
					importSetup.setCaregiverContactInfoCreated((Boolean) handlingEvent.getAttributes().get("caregiverContactInfoCreated"));
					if (importSetup.isCaregiverContactInfoCreated()) {
						importSetup.setCaregiverContactInfo((ContactInfo) handlingEvent.getAttributes().get("caregiverContactInfo"));
					}
				}
				
				// support importing two caregivers (e.g. Mother and Father for child patients)				
				if ((handlingEvent = caregiverExistsHandling(context, errors, importDefinition, importSetup, importLog, 
						importSetup.getIndexCaregiver2FirstName(), importSetup.getIndexCaregiver2LastName(), 
						importSetup.getIndexCaregiver2ContactInfoAddress(), importSetup.getIndexCaregiver2ContactInfoCity(),
						importSetup.getIndexCaregiver2ContactInfoState(), importSetup.getIndexCaregiver2ContactInfoZip(),
						importSetup.getIndexCaregiver2ContactInfoPhone1(), importSetup.getIndexCaregiver2ContactInfoEmail(),
						lineNum)).getId().equals(ERROR_FLOW_EVENT_ID)) {
					this.updateEntityCounts(importSetup, importLog);
					continue;
				}
				importSetup.setCaregiver2Created((Boolean) handlingEvent.getAttributes().get("caregiverCreated"));
				importSetup.setCaregiver2Existed((Boolean) handlingEvent.getAttributes().get("caregiverExisted"));
				if (importSetup.isCaregiver2Created() || importSetup.isCaregiver2Existed()) {
					importSetup.setCaregiver2((Caregiver) handlingEvent.getAttributes().get("caregiver"));
					importSetup.setCaregiver2ContactInfoCreated((Boolean) handlingEvent.getAttributes().get("caregiverContactInfoCreated"));
					if (importSetup.isCaregiver2ContactInfoCreated()) {
						importSetup.setCaregiver2ContactInfo((ContactInfo) handlingEvent.getAttributes().get("caregiverContactInfo"));
					}
				}

				// determine if Patient is Enrolled in Project. possibly create new EnrollmentStatus
				if ((handlingEvent = enrollmentStatusExistsHandling(context, errors, importDefinition, importSetup, importLog, lineNum)).getId().equals(ERROR_FLOW_EVENT_ID)) {
					this.updateEntityCounts(importSetup, importLog);
					continue;
				}
						
					
				Event instrHandlingEvent = null;
				if (!importDefinition.getPatientOnlyImport()) {
			
					// find matching Visit. possibly create new Visit
					if ((handlingEvent = visitExistsHandling(context, errors, importDefinition, importSetup, importLog, lineNum)).getId().equals(ERROR_FLOW_EVENT_ID)) {
						this.updateEntityCounts(importSetup, importLog);
						continue;
					}

					// find matching instrument. possibly create new instrument. type of instrument specified in the 
					// importDefinition
					if ((instrHandlingEvent = instrumentExistsHandling(context, errors, importDefinition, importSetup, importLog, lineNum)).getId().equals(ERROR_FLOW_EVENT_ID)) {
						// it is simply enough to check for the existence of the "alreadyExists" attribute, i.e. do not need to check its value
						// note: if instrument exists but not data entered it is not considered as already existing because data is imported
						// into the instrument since it is not overwriting anything, and already existing refers to the existence of data such
						// that the import record should not be imported because it already exists
						if (instrHandlingEvent.getAttributes() != null && instrHandlingEvent.getAttributes().get("alreadyExists") != null) {
							importLog.incAlreadyExist();
						}
						this.updateEntityCounts(importSetup, importLog);
						continue;
					}
				}


//RIGHT HERE
// X-do pedi attachments (consents) when working in the following with LavaFile stuff				
//   download definition mapping file
//	 download data file				

// X-change mapping file format to 3 rows: row 2 is entity type, row 3 is property name (if
//  both are blank then defaults to 1st instrument and prop name == column name (row 1))				

// X-pedi new patient history import (data file with all columns, not cut off at 256 cols)
// X-should only create caregiver and caregiver contactInfo records if data exists
// X-log totals do not reflect caregivers and contactInfo records (but they could. not bothering with
//   caregiverContactInfo totals since that is tightly bound with caregiver)
// X-SPDC History Form 2 metadata populated, e.g. marco_lab...history_timestamp (which will be used for versioning)				
// X-SPDC History Form 2 only showing when run server in non-debug mode - KNOWN ISSUE				
// X-confirm that data is being loaded correctly (incl. caregiver livesWithPatient, ContactInfo is
//   for caregiver)
// need separate definitions for old and current versions because var names from old
//  need to map to current, e.g. field5 old maps to field6 current, whereas for current
//  field5 maps to field5		
				
// X-use opencsv				
	
// OT: Add Patient skip logic on Community Dx should disable following field unless "6 - other"			
				
// pertinent TODOs in code, config, Hibernate mapping, jsp, etc.
				
// validation check: compare DOB to form data collection date, as sometimes user enters current date for DOB
				
// instrument property case mismatch mapping vs. reality: this is not a record level warning so
//   how to report as do not want to repeat for each record of data file. only report these warnings
//   for the first import record, and do not incWarnings; instead add a new summary counter to 
//   propCaseMismatchCount to CrmsImportLog 				

// X-make mapping definition name longer (50?)				
// import definition UI cleanup (for now move Project near top, ahead of selection of Import
//  skip logic:
//      if Only Import Patients then disable Visit and Instrument fields
//      if Patient Must Not Exist rule is selected then other exist rules should be disabled and
//         set to Must Not Exist
//      if Caregiver Instrument then enable Caregiver Instrument Exist Rule
// X-get rid of import section, default to imports section (make sure regular import fails
//  on SPDC history import)				
// TODO: make the mapping data file bind with Spring so on refresh it is not lost, e.g. when a required
//  field error on anohter field. keep in mind that tried this already using the Spring facility to bind
//  an uploaded file but ran into problems integrating that into our implementation of uploading files
//  so this is not a minor item
// why does Browse button have _ in it?)				
// help text: (maybe) if mapping file changes, have to re-upload
// importLogContent / crmsImportLogContent format log summary results in a table
// importLog/crmsImportLog needs to get rid of Edit button
// allow Edit of import log where only the Notes field can be edited so can put in notes about things that happened in import				
// X-crmsAllImportLogs needs a QuickFilter
// test that deleting import definition deletes the mapping file
// X-add creation of entities as importLog CREATED messages
// ?? create preview mode, at least for development, that does not do anything to db				

// default import definition field values in PediImportDefinitionHandler to the common case of 
// importing instrument data. is caregiver instrument the common case?
				
// X-call calculate on save (or is it done automatically?)
				
// X-custom, hard-coded truncation for certain pediLAVA imports, e.g. Sensory Profile Child
// future plan is to use importDefinition truncate flag (already added to schema) to either truncate or 
// abort record on error if database truncation exception thrown. to truncate will consult the metadata
// for each property to find the maxLength
				
// other majors:
// X-FileMaker patient import
// X-FileMaker Sensory Profile Child import
// X-REDCap Sensory Profile Child import
// X-for REDCap assessment imports, test patient firstName against nickname if match against firstName failed
// BASC import			
				
// Rankin TODOs:
//   migrate to MAC LAVA
//				
//   implement startDataRow (defaults to 2 for all imports done prior to implementation)
//   UPDATE: believe this is needed for pedi BASC too				
//				
//   match existing Visit on Visit Type if user sets flag to do so. even if not, Visit Type could still be
//   used when creating new Visits (default is false)				
//   (columns and metadata to support already added to db)
//				
//   match existing	Visit on user specified time window, in days, around the visitDate in data file. set
//   to 0 for an exact date match (need info text with this) (0 is the default)				
//   (columns and metadata to support already added to db)
//				
//   expand to work with multiple instruments (crmsImportDefinition will have inputs for up to 10 
//   instruments, and will have to rework instrumentExistsHandling to go thru each specified instrument,
//   and use of instrType,instrVer for generateLocation for data files will just have to use that of
//   the first instrument chosen)
//   (columns and metadata to support already added to db, i.e. 2 thru 10 instrType/instrVer)
//				
// 2.0  expand to work with files in folders for special not-exactly-import use cases:
//      a) for instruments that load individual patient files, e.g. e-prime instruments
//      b) for PDFs that should be attached to an existing instrument
// 2.0: validation, i.e. read property metadata to obtain type, list of valid values
// 
// 3.0 import detail data files, e.g. Freesurfer 5.1 data				
				
				if ((handlingEvent = otherExistsHandling(context, errors, importDefinition, importSetup, importLog, lineNum)).getId().equals(ERROR_FLOW_EVENT_ID)) {
					this.updateEntityCounts(importSetup, importLog);
					continue;
				}
				
				// iterate thru the values of the current import record, setting each value on the property of an entity, as 
				// determined by the importDefinition mapping file
				if ((handlingEvent = setPropertyHandling(context, errors, importDefinition, importSetup, importLog, lineNum)).getId().equals(ERROR_FLOW_EVENT_ID)) {
					continue;
				}

				// if patient only import and nothing was created then nothing to persist
				if (importDefinition.getPatientOnlyImport()) {
					if (!importSetup.isPatientCreated() && !importSetup.isContactInfoCreated() && !importSetup.isCaregiverCreated() 
							&& !importSetup.isCaregiver2Created() && !importSetup.isEnrollmentStatusCreated()) {
						importLog.incAlreadyExist();
						this.updateEntityCounts(importSetup, importLog);
						continue;
					}
				}
				
				
				// if definition has flag set that this is a caregiver instrument, set the caregiver on the instrument
				if (importDefinition.getInstrCaregiver() != null && importDefinition.getInstrCaregiver()) {
					if ((handlingEvent = setInstrumentCaregiver(context, errors, importDefinition, importSetup, importLog, lineNum)).getId().equals(ERROR_FLOW_EVENT_ID)) {
						continue;
					}
				}

				
				
				// at this point all values of the import record have been successfully set on entity properties

				//TODO: when enable updating existing instrument data:
				// not calling save on the entity should solve not persisting new records that should be skipped
				// but what if existing entities are modified and then record is to be skipped?
				// Hibernate would implicitly save changes so would have to explicitly rollback. 
				// however, use cases don't support modifying existing Patient/ES/Visit, only an existing Instrument
				// so review how CRUD editing cancel is done and try calling refresh on the modified object 
				// (could fool around with CRUD editing and take out refresh on cancel just to see if changes 
				// are persisted without explicit call to save)
				if ((handlingEvent = saveImportRecord(importDefinition, importSetup, importLog, lineNum)).getId().equals(ERROR_FLOW_EVENT_ID)) {
					continue;
				}
				
				// update counts
				
				// applies to entire import record
				// it is simply enough to check for the existence of the "update" attribute, i.e. do not need to check its value
				if (!importDefinition.getPatientOnlyImport()) {
					if (instrHandlingEvent.getAttributes() != null && instrHandlingEvent.getAttributes().get("update") != null) {
						importLog.incUpdated();
					}
					else {
						importLog.incImported();
					}
				}
				else {
					// before saving already handled situation where nothing created for patient only import, so
					// know if got this far that something was created, thus increment the import count
					importLog.incImported();
				}
				
				// these counts apply to specific entities within an import record. note that if processing was aborted for an import 
				// record the entity counts are still updated because want to know what entities already existed, etc. this is done
				// above where this method is called before every "continue" statement that follows a handling method that matches
				// an existing entity or creates a new entity
				updateEntityCounts(importSetup, importLog);
			}
		}

		// at this point, returnEvent success means the success of the overall import. individual records 
		// may have had errors, which are logged as importLog messages and the total error count is incremented
		// returnEvent error means that the import failed as a whole and error msg is put in the command
		// object errors to be displayed
		if (returnEvent.getId().equals(SUCCESS_FLOW_EVENT_ID)) {
			importLog.save();
			context.getFlowScope().put("importLogId", importLog.getId());
		}
		
		return returnEvent;
	}	
	

	protected Event validateDataFile(BindingResult errors, ImportDefinition importDefinition, ImportSetup importSetup) throws Exception {
		CrmsImportSetup crmsImportSetup = (CrmsImportSetup) importSetup;
		
		if (super.validateDataFile(errors, importDefinition, importSetup).getId().equals(ERROR_FLOW_EVENT_ID)) {
			return new Event(this, ERROR_FLOW_EVENT_ID);
		}
				
		// set indices here as this only needs to be done once for the entire data file
		
		// ** the import definition mapping file second row must have entity string and third row must have 
		// property string that match exactly the entity and property name strings below  
	
		// look up the indices of fields in the import definition mapping file properties row that are required 
		// to search for existing entities and/or populate new entities, and record the indices to be used in 
		// processing each import record
		// required fields for creating new Patient/EnrollmentStatus/Visit/instrument which could have the same 
		// uniform value across all records imported from a data file may be specified as part of the import 
		// definition rather then being supplied in the data file. but the data file takes precedent so first 
		// check the data file and set the index if the field has a value in the data file.
		
		// note that the entity and property are on separate lines of the mapping file and thus in separate arrays,
		// so need to check the two arrays in conjunction with each other (could have just had a single property
		// row with entity.property but if there are multiple instruments in the data file with many properties, 
		// easier to edit the mapping file with instrument names across the entity column headers instead of 
		// editing entity.property format for each property)
		
		setDataFilePropertyIndex(importSetup, "indexPatientPIDN", "patient", "PIDN");
		setDataFilePropertyIndex(importSetup, "indexPatientFirstName", "patient", "firstName");
		setDataFilePropertyIndex(importSetup, "indexPatientLastName", "patient", "lastName");
		setDataFilePropertyIndex(importSetup, "indexPatientBirthDate", "patient", "birthDate");
		setDataFilePropertyIndex(importSetup, "indexPatientGender", "patient", "gender");

		setDataFilePropertyIndex(importSetup, "indexContactInfoAddress", "contactInfo", "address");
		setDataFilePropertyIndex(importSetup, "indexContactInfoCity", "contactInfo", "city");
		setDataFilePropertyIndex(importSetup, "indexContactInfoState", "contactInfo", "state");
		setDataFilePropertyIndex(importSetup, "indexContactInfoZip", "contactInfo", "zip");
		setDataFilePropertyIndex(importSetup, "indexContactInfoPhone1", "contactInfo", "phone1");
		setDataFilePropertyIndex(importSetup, "indexContactInfoEmail", "contactInfo", "email");

		setDataFilePropertyIndex(importSetup, "indexCaregiverFirstName", "caregiver", "firstName");
		setDataFilePropertyIndex(importSetup, "indexCaregiverLastName", "caregiver", "lastName");
		setDataFilePropertyIndex(importSetup, "indexCaregiverContactInfoAddress", "caregiverContactInfo", "address");
		setDataFilePropertyIndex(importSetup, "indexCaregiverContactInfoCity", "caregiverContactInfo", "city");
		setDataFilePropertyIndex(importSetup, "indexCaregiverContactInfoState", "caregiverContactInfo", "state");
		setDataFilePropertyIndex(importSetup, "indexCaregiverContactInfoZip", "caregiverContactInfo", "zip");
		setDataFilePropertyIndex(importSetup, "indexCaregiverContactInfoPhone1", "caregiverContactInfo", "phone1");
		setDataFilePropertyIndex(importSetup, "indexCaregiverContactInfoEmail", "caregiverContactInfo", "email");
		
		setDataFilePropertyIndex(importSetup, "indexCaregiver2FirstName", "caregiver2", "firstName");
		setDataFilePropertyIndex(importSetup, "indexCaregiver2LastName", "caregiver2", "lastName");
		setDataFilePropertyIndex(importSetup, "indexCaregiver2ContactInfoAddress", "caregiver2ContactInfo", "address");
		setDataFilePropertyIndex(importSetup, "indexCaregiver2ContactInfoCity", "caregiver2ContactInfo", "city");
		setDataFilePropertyIndex(importSetup, "indexCaregiver2ContactInfoState", "caregiver2ContactInfo", "state");
		setDataFilePropertyIndex(importSetup, "indexCaregiver2ContactInfoZip", "caregiver2ContactInfo", "zip");
		setDataFilePropertyIndex(importSetup, "indexCaregiver2ContactInfoPhone1", "caregiver2ContactInfo", "phone1");
		setDataFilePropertyIndex(importSetup, "indexCaregiver2ContactInfoEmail", "caregiver2ContactInfo", "email");
		
		
		setDataFilePropertyIndex(importSetup, "indexEsStatusDate", "enrollmentStatus", "date");
		setDataFilePropertyIndex(importSetup, "indexEsStatus", "enrollmentStatus", "status");
		setDataFilePropertyIndex(importSetup, "indexVisitDate", "visit", "visitDate");
		setDataFilePropertyIndex(importSetup, "indexVisitTime", "visit", "visitTime");
		setDataFilePropertyIndex(importSetup, "indexVisitType", "visit", "visitType");
		setDataFilePropertyIndex(importSetup, "indexVisitWith", "visit", "visitWith");
		setDataFilePropertyIndex(importSetup, "indexVisitLoc", "visit", "visitLoc");
		setDataFilePropertyIndex(importSetup, "indexVisitStatus", "visit", "visitStatus");
		
		//TODO: when support multiple instruments for a single import, need separate instrDcDate and instrDcStatus properties, in
		// conjunction with how handling and setting properties on multiple instruments will be done in general				
		setDataFilePropertyIndex(importSetup, "indexInstrDcDate", "instrument", "dcDate");
		setDataFilePropertyIndex(importSetup, "indexInstrDcStatus", "instrument", "dcStatus");

		setOtherIndices((CrmsImportDefinition)importDefinition, crmsImportSetup);

		//TODO: move these checks to the CrmsImportDefinitionHandler
		// error on entire import if either no PIDN or no FirstName/LastName 			
		if (crmsImportSetup.getIndexPatientPIDN() == -1 && 
				(crmsImportSetup.getIndexPatientFirstName() == -1 || crmsImportSetup.getIndexPatientLastName() == -1)) {
			LavaComponentFormAction.createCommandError(errors, "Insufficient Patient properties (must have PIDN or FirstName Lastname) in Import Definition mapping file");
			return new Event(this, ERROR_FLOW_EVENT_ID);
		}
		// error on entire import if no visitDate 			
		else if (!((CrmsImportDefinition)importDefinition).getPatientOnlyImport() && crmsImportSetup.getIndexVisitDate() == -1) {
			LavaComponentFormAction.createCommandError(errors, "Import Definition mapping file must have 'visit.visitDate' property to link import record to a date");
			return new Event(this, ERROR_FLOW_EVENT_ID);
		}

		return new Event(this, SUCCESS_FLOW_EVENT_ID);
	}
	

	
	/**
	 * Subclasses should override to generate custom projName
	 * 
	 * @return
	 */
	protected void generateRevisedProjName(CrmsImportDefinition importDefinition, CrmsImportSetup importSetup) {
		importSetup.setRevisedProjName(importDefinition.getProjName());
	}

	
	/**
	 * Subclasses override this to set indices for custom imports. 
	 * 
	 * @param importDefinition
	 * @param importSetup
	 * @throws Exception
	 */
	protected void setOtherIndices(CrmsImportDefinition importDefinition, CrmsImportSetup importSetup) throws Exception {
		// do nothing
	}


	
	
	/**
	 * patientExistsHandling
	 * 
	 * Determine whether patient exists or not and act accordingly based on the importDefinition settings.
	 * 
	 * The approach to logging is to log the error when it occurs within the method but have the 
	 * caller increment the error count if an error Event is returned (in which case processing of 
	 * the current record will abort and will go to the next import record).
	 * 
	 * @param context
	 * @param errors
	 * @param importDefinition
	 * @param importSetup
	 * @param lineNum
	 * @return SUCCESS Event if no import errors with current record; ERROR EVENT if errors
	 */
	protected Event patientExistsHandling(RequestContext context, BindingResult errors, 
			CrmsImportDefinition importDefinition, CrmsImportSetup importSetup, CrmsImportLog importLog,
			int lineNum) {
		HttpServletRequest request =  ((ServletExternalContext)context.getExternalContext()).getRequest();
		LavaDaoFilter filter = EntityBase.newFilterInstance();
		SimpleDateFormat formatter = new SimpleDateFormat(importDefinition.getDateFormat() != null ? importDefinition.getDateFormat() : DEFAULT_DATE_FORMAT);
		String dateOrTimeAsString = null;
		Date birthDate = null;

		// search for existing patient
		Patient p = null;

		filter.clearDaoParams();
		if (importSetup.getIndexPatientPIDN() != -1) {
			String pidnAsString = importSetup.getDataValues()[importSetup.getIndexPatientPIDN()];
			Long pidn = null;
			try {
				pidn = Long.valueOf(pidnAsString);
			} catch (NumberFormatException ex) {
				importLog.addErrorMessage(lineNum, "PIDN Is not a number="+ pidnAsString);
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}
			filter.addIdDaoEqualityParam(pidn);
			p = (Patient) Patient.MANAGER.getById(pidn);
		}
		else { 
			// birthDate is optional for search as it is often not part of data files
			if (importSetup.getIndexPatientBirthDate() != -1) {
				dateOrTimeAsString = importSetup.getDataValues()[importSetup.getIndexPatientBirthDate()];
				formatter.setLenient(true); // to avoid exceptions; we check later to see if leniency was applied
				try {
					birthDate = formatter.parse(dateOrTimeAsString);
				} catch (ParseException e) {
					// likely will not be called with leniency applied
					importLog.addErrorMessage(lineNum, "Patient.birthDate is an invalid Date format, Date:" + dateOrTimeAsString);
					return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
				}
				
				// because if date format is yyyy for year part, the parser will allow any date into the future, even 5 digit dates, so 
				// have to do range checking to catch bad date errors
				java.util.Calendar birthDateCalendar = java.util.Calendar.getInstance();
				birthDateCalendar.setTime(birthDate);
				int birthDateYear = birthDateCalendar.get(java.util.Calendar.YEAR);
				java.util.Calendar nowCalendar = java.util.Calendar.getInstance();
				int nowYear = nowCalendar.get(java.util.Calendar.YEAR);
				if (birthDateYear < (nowYear - 100) || birthDateYear > nowYear) {
					importLog.addErrorMessage(lineNum, "Patient DOB has an invalid Year. DOB:" + dateOrTimeAsString);
					return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
				}

				filter.addDaoParam(filter.daoEqualityParam("birthDate", birthDate));
			}

			// have already validated that firstName and lastName are present in the mapping definition file if PIDN is not
			setPatientNameMatchFilter(filter, importSetup);
			
			try {
				p = (Patient) Patient.MANAGER.getOne(filter);
			}
			// this should never happen. if re-running import of a data file, should just be one 
			catch (IncorrectResultSizeDataAccessException ex) {
				importLog.addErrorMessage(lineNum, "Duplicate Patient records for patient firstName:" + importSetup.getDataValues()[importSetup.getIndexPatientFirstName()] +
						" lastName:" + importSetup.getDataValues()[importSetup.getIndexPatientLastName()]); 
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}
			
			// if Patient not found, then determine if it is because of a mismatch in birthDate. if this is the case then return
			// an ERROR rather than continuing to create a new Patient, because most often in the case where the first and last
			// name match a Patient but there is no match when the birthDate is also considered it is because of a data entry 
			// error on the birthDate (either in the data file or in LAVA).
			
			// so in this case where the names match without considering the birthDate it is better to return an ERROR rather
			// than create a new Patient because it is likely that the new Patient will be a duplicate of an existing Patient
			// it is pain to remove a duplicate Patient with data and either merge that data with the pre-existing Patient
			// or remove it. better process is that the user will see the error on import, the record will not be imported,
			
			// the user will fix the birthDate in the data file or in LAVA, import the data file again, and the data will 
			// correctly be imported to the pre-existing Patient
			
			// the only exception to this would be if there are in fact two patients with the same name, but in this case, the
			// query here would detect dups and return a Duplicate Patients error, and then the user would need to fix 
			// the data to accommodate that (most likely by fixing the birthDate in the data file or in LAVA, but possibly
			// by modifying patient first or last name if that made sense)
			
			// only do this if there is a birthDate in the date file because otherwise the match will have already been
			// done on first and last name without considering birthDate
			if (p == null) {
				if (importSetup.getIndexPatientBirthDate() != -1) {
					filter.clearDaoParams();
					setPatientNameMatchFilter(filter, importSetup);
					try {
						p = (Patient) Patient.MANAGER.getOne(filter);
					}
					// this should never happen. if re-running import of a data file, should just be one 
					catch (IncorrectResultSizeDataAccessException ex) {
						importLog.addErrorMessage(lineNum, "Duplicate Patient records for patient firstName:" + importSetup.getDataValues()[importSetup.getIndexPatientFirstName()] +
								" lastName:" + importSetup.getDataValues()[importSetup.getIndexPatientLastName()]); 
						return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
					}
					if (p != null) {
						importLog.addErrorMessage(lineNum, "Patient birth date may be incorrect in either data file (" + dateOrTimeAsString + ") or LAVA (" + 
								formatter.format(p.getBirthDate()) + "). Patient firstName:" + importSetup.getDataValues()[importSetup.getIndexPatientFirstName()] +
								" lastName:" + importSetup.getDataValues()[importSetup.getIndexPatientLastName()] + " birthDate:" + dateOrTimeAsString);
						return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
					}
				}
			}
		}
			
		if (p == null) {
			if (importDefinition.getPatientExistRule().equals(MUST_EXIST)) {
				if (importSetup.getIndexPatientPIDN() != -1) {
					importLog.addErrorMessage(lineNum, "Patient does not exist violating MUST_EXIST flag. PIDN:" + importSetup.getDataValues()[importSetup.getIndexPatientPIDN()]); 
				}
				else {
					importLog.addErrorMessage(lineNum, "Patient does not exist violating MUST_EXIST flag.Line:" +  
						" First Name:" + importSetup.getDataValues()[importSetup.getIndexPatientFirstName()] + " Last Name:" + importSetup.getDataValues()[importSetup.getIndexPatientLastName()]);
				}
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}else {
				// for either MUST_NOT_EXIST or MAY_OR_MAY_NOT_EXIST instantiate the Patient
				
				if (importSetup.getIndexPatientFirstName() == -1 || !StringUtils.hasText(importSetup.getDataValues()[importSetup.getIndexPatientFirstName()])) {
					importLog.addErrorMessage(lineNum, "Cannot create Patient. First Name field (patient.firstName) is missing or has no value");
					return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
				}
				if (importSetup.getIndexPatientLastName() == -1 || !StringUtils.hasText(importSetup.getDataValues()[importSetup.getIndexPatientLastName()])) {
					importLog.addErrorMessage(lineNum, "Cannot create Patient. Last Name field (patient.lastName) is missing or has no value");
					return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
				}
				if (importSetup.getIndexPatientBirthDate() == -1 || !StringUtils.hasText(importSetup.getDataValues()[importSetup.getIndexPatientBirthDate()])) {
					importLog.addErrorMessage(lineNum, "Cannot create Patient. Date of Birth field (patient.birthDate) is missing or has no value");
					return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
				}
				if (importSetup.getIndexPatientGender() == -1 || !StringUtils.hasText(importSetup.getDataValues()[importSetup.getIndexPatientGender()])) {
					importLog.addErrorMessage(lineNum, "Cannot create Patient. Gender field (patient.gender) is missing or has no value");
					return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
				}
					
				// create Patient record
				p = createPatient(importDefinition, importSetup);
					
				// the property values will be assigned when iterating dataValues below. could assign
				// the "indexed" Patient properties here since those were found as part of determing whether the
				// Patient exists. but still need to assign other Patient properties (can not index them
				// all because do not even know what all the properties could be) and since will be 
				// iterating thru all data values just assign all properties when iterating dataValues

				// however, do set first name, last name, dob properties as they may be used in error log
				p.setFirstName(importSetup.getDataValues()[importSetup.getIndexPatientFirstName()]);
				p.setLastName(importSetup.getDataValues()[importSetup.getIndexPatientLastName()]);
				p.updateCalculatedFields(); // so can use full name in log messages
				// if the birthDate conversion was not done yet, i.e. PIDN was supplied such that a PIDN match was done (and failed)
				if (birthDate == null) {
					//TODO: look at making this date conversion into a small helper method									
					dateOrTimeAsString = importSetup.getDataValues()[importSetup.getIndexPatientBirthDate()];
					formatter.setLenient(true); // to avoid exceptions; we check later to see if leniency was applied
					try {
						birthDate = formatter.parse(dateOrTimeAsString);
					} catch (ParseException e) {
						// likely will not be called with leniency applied
						importLog.addErrorMessage(lineNum, "Patient.birthDate is an invalid Date format, Date:" + dateOrTimeAsString);
						return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
					}
				}
				p.setBirthDate(birthDate);
				// at this point have already validated that patient.gender exists in import file and has a value
				p.setGender(importSetup.getDataValues()[importSetup.getIndexPatientGender()].toLowerCase().startsWith("m") || 
						importSetup.getDataValues()[importSetup.getIndexPatientGender()].equals("1") ? (byte)1 : (byte)2);
				p.setCreated(new Date());
				p.setCreatedBy("IMPORT (" + CoreSessionUtils.getCurrentUser(sessionManager, request).getLogin() + ")");
				
				importSetup.setPatientCreated(true);
				importSetup.setPatient(p);
			}
		}
		else { // Patient already exists
			importSetup.setPatientExisted(true);
			importSetup.setPatient(p);
			if (importDefinition.getPatientExistRule().equals(MUST_NOT_EXIST)) {
				// typically with this flag the first time the import is run the Patients will not exist
				// so they will be created above. if there were some import data errors they would be fixed
				// and the script re-imported, at which point there will be these errors for all Patients that 
				// were created on first run, so record will be skipped and Patient will correctly not be
				// created again
					
				// note: this is why it is important that Patient should not be persisted until EnrollmentStatus,
				// Visit and Instrument have all been validated for errors and successfully added. because if 
				// Patient were persisted, then there were errors with Visit, Instrument, etc. when those errors 
				// were fixed and script re-imported the records will be skipped because Patient now exists
				
				// note: this differs from MAY_OR_MAY_NOT_EXIST where import of the record will continue if
				// the Patient exists (as well as if Patient does not exist as it will be created above)
				importLog.addErrorMessage(lineNum, "Patient already exists, violates Import Definition MUST_NOT_EXIST setting. Patient::" + p.getFullNameWithId());
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}
		}
		
		return new Event(this, SUCCESS_FLOW_EVENT_ID);
	}


	/**
	 * Set the filter for matching patient names. Subclasses should override if they have custom 
	 * patient name matching.
	 * 
	 * @param filter
	 * @param importSetup
	 */
	protected void setPatientNameMatchFilter(LavaDaoFilter filter, CrmsImportSetup importSetup) {
		//TODO: consider Danny's Levenson algorithm for fuzzy matching Patient Last Name
		filter.addDaoParam(filter.daoEqualityParam("firstName", importSetup.getDataValues()[importSetup.getIndexPatientFirstName()]));
		filter.addDaoParam(filter.daoEqualityParam("lastName", importSetup.getDataValues()[importSetup.getIndexPatientLastName()]));
	}

	
	/**
	 * contactInfoExistsHandling
	 * 
	 * Determine whether ContactInfo should be created, and if so create instance. Subclasses can override to customize
	 * logic to determine whether to create a ContactInfo instance.
	 * 
	 * @param context
	 * @param errors
	 * @param importDefinition
	 * @param importSetup
	 * @param lineNum
	 * @return SUCCESS Event if no import errors with current record; ERROR EVENT if errors
	 */
	protected Event contactInfoExistsHandling(RequestContext context, BindingResult errors, 
			CrmsImportDefinition importDefinition, CrmsImportSetup importSetup, CrmsImportLog importLog,
			int lineNum) {
		
		// the assumption is that ContactInfo is only imported as part of a new Patient import, so if a new Patient was created
		// and any ContactInfo properties are mapped and have data then create a new ContactInfo record.
		// because currently not a use case for importing new ContactInfo for already existing Patients
		if (importSetup.isPatientCreated()) {
			// check that at least one of what are considered the key ContactInfo properties has data
			if ((importSetup.getIndexContactInfoAddress() != -1 && StringUtils.hasText(importSetup.getDataValues()[importSetup.getIndexContactInfoAddress()])) || 
					(importSetup.getIndexContactInfoCity() != -1 && StringUtils.hasText(importSetup.getDataValues()[importSetup.getIndexContactInfoCity()])) ||  
					(importSetup.getIndexContactInfoState() != -1 && StringUtils.hasText(importSetup.getDataValues()[importSetup.getIndexContactInfoState()])) || 
					(importSetup.getIndexContactInfoZip() != -1 && StringUtils.hasText(importSetup.getDataValues()[importSetup.getIndexContactInfoZip()])) ||
					(importSetup.getIndexContactInfoPhone1() != -1 && StringUtils.hasText(importSetup.getDataValues()[importSetup.getIndexContactInfoPhone1()])) || 
					(importSetup.getIndexContactInfoEmail() != -1 && StringUtils.hasText(importSetup.getDataValues()[importSetup.getIndexContactInfoEmail()]))) {
				ContactInfo contactInfo = createContactInfo(importDefinition, importSetup);
				contactInfo.setPatient(importSetup.getPatient());
				contactInfo.setIsCaregiver(false);
				contactInfo.setActive((short)1);
				if (importSetup.getIndexContactInfoAddress() != -1) {
					contactInfo.setAddress(importSetup.getDataValues()[importSetup.getIndexContactInfoAddress()]);
				}
				if (importSetup.getIndexContactInfoCity() != -1) {
					contactInfo.setCity(importSetup.getDataValues()[importSetup.getIndexContactInfoCity()]);
				}
				if (importSetup.getIndexContactInfoState() != -1) {
					contactInfo.setState(this.convertStateCode(importSetup.getDataValues()[importSetup.getIndexContactInfoState()], importLog, lineNum));
				}
				if (importSetup.getIndexContactInfoZip() != -1) {
					contactInfo.setZip(importSetup.getDataValues()[importSetup.getIndexContactInfoZip()]);
				}
				if (importSetup.getIndexContactInfoPhone1() != -1) {
					contactInfo.setPhone1(importSetup.getDataValues()[importSetup.getIndexContactInfoPhone1()]);
				}
				if (importSetup.getIndexContactInfoEmail() != -1) {
					contactInfo.setEmail(importSetup.getDataValues()[importSetup.getIndexContactInfoEmail()]);
				}
				importSetup.setContactInfoCreated(true);
				importSetup.setContactInfo(contactInfo);
			}
		}
	
		return new Event(this, SUCCESS_FLOW_EVENT_ID);
	}

	
	/**
	 * caregiverExistsHandling
	 * 
	 * Determine whether Caregiver exists or not and create if it does not. Can assume an exists setting
	 * of MAY_OR_MAY_NOT_EXIST where nothing is updated if the Caregiver already exists.
	 * 
	 * @param context
	 * @param errors
	 * @param importDefinition
	 * @param importSetup
	 * @param lineNum
	 * @return SUCCESS Event if no import errors with current record; ERROR EVENT if errors
	 */
	protected Event caregiverExistsHandling(RequestContext context, BindingResult errors, 
			CrmsImportDefinition importDefinition, CrmsImportSetup importSetup, CrmsImportLog importLog,
			int indexFirstName, int indexLastName, int indexContactInfoAddress, int indexContactInfoCity,
			int indexContactInfoState, int indexContactInfoZip, int indexContactInfoPhone1, int indexContactInfoEmail,
			int lineNum) {
		LavaDaoFilter filter = EntityBase.newFilterInstance();

		// search for existing Caregiver
		Caregiver caregiver = null;
		Boolean caregiverCreated = null;
		Boolean caregiverExisted = null;
		ContactInfo caregiverContactInfo = null;
		Boolean caregiverContactInfoCreated = null;

		if (indexFirstName == -1 || !StringUtils.hasText(importSetup.getDataValues()[indexFirstName]) 
			|| indexLastName == -1 || !StringUtils.hasText(importSetup.getDataValues()[indexLastName])) {
			// if no caregiver data in the data file cannot check if caregiver exists or create a new
			// caregiver
			caregiverCreated = false;
			caregiverExisted = false;
			caregiverContactInfoCreated = false;
		}
		else {
			
			// only search if the Patient already exists because if Patient did not exist then Caregiver does not exist
			if (!importSetup.isPatientCreated()) { 
				filter.clearDaoParams();
				filter.setAlias("patient", "patient");
				filter.addDaoParam(filter.daoEqualityParam("patient.id", importSetup.getPatient().getId()));
				filter.addDaoParam(filter.daoEqualityParam("firstName", importSetup.getDataValues()[indexFirstName]));
				filter.addDaoParam(filter.daoEqualityParam("lastName", importSetup.getDataValues()[indexLastName]));
				try {
					caregiver = (Caregiver) Caregiver.MANAGER.getOne(filter);
				}
				// this should never happen. if re-running import of a data file, should just be one 
				catch (IncorrectResultSizeDataAccessException ex) {
					importLog.addErrorMessage(lineNum, "Duplicate Caregiver records for patient " + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + 
							" and Caregiver firstName:" + importSetup.getDataValues()[indexFirstName] 
							+ " lastName:" + importSetup.getDataValues()[indexLastName]);
					return new Event(this, "error"); // to abort processing this import record
				}
			}
			
			// logic to determine whether a Caregiver should be created if there is no match, so cover the conditions where a 
			// Caregiver could be imported:
			// if this is a patient only import then yes
			// if this is an assessment import and it is a caregiver instrument and existRule is MAY_OR_MAY_NOT_EXIST then yes (as 
			//   opposed to MUST_EXIST)
			if (importDefinition.getPatientOnlyImport() || 
					(importDefinition.getInstrCaregiver() && importDefinition.getInstrCaregiverExistRule().equals(MAY_OR_MAY_NOT_EXIST))) { 
				if (caregiver == null) {
					caregiverExisted = false;
					
					// at this point either Patient was just created in which case there cannot be a Caregiver yet, or Patient
					// already existed but Caregiver could not be found
		
					if ((indexFirstName != -1 && StringUtils.hasText(importSetup.getDataValues()[indexFirstName])) && 
							(indexLastName != -1 && StringUtils.hasText(importSetup.getDataValues()[indexLastName]))) {
						caregiver = createCaregiver(importDefinition, importSetup);
						caregiver.setPatient(importSetup.getPatient());
						caregiver.setFirstName(importSetup.getDataValues()[indexFirstName]);
						caregiver.setLastName(importSetup.getDataValues()[indexLastName]);
						caregiver.setActive((short)1);
						// any other (non required) caregiver fields will be assigned in setProperty as they are encountered 
						// in the import record
						caregiverCreated = true;
					
						// if a new Caregiver is created, create a new ContactInfo record for that Caregiver if there is
						// ContactInfo data in the import data file
						// caregiverContactInfo properties are set in setPropertyHandling
						if ((indexContactInfoAddress != -1 && StringUtils.hasText(importSetup.getDataValues()[indexContactInfoAddress])) || 
							(indexContactInfoCity != -1 && StringUtils.hasText(importSetup.getDataValues()[indexContactInfoCity])) ||  
							(indexContactInfoState != -1 && StringUtils.hasText(importSetup.getDataValues()[indexContactInfoState])) || 
							(indexContactInfoZip != -1 && StringUtils.hasText(importSetup.getDataValues()[indexContactInfoZip])) ||
							(indexContactInfoPhone1 != -1 && StringUtils.hasText(importSetup.getDataValues()[indexContactInfoPhone1])) || 
							(indexContactInfoEmail != -1 && StringUtils.hasText(importSetup.getDataValues()[indexContactInfoEmail]))) {
					
							caregiverContactInfo = createContactInfo(importDefinition, importSetup);
							caregiverContactInfo.setPatient(importSetup.getPatient());
							caregiverContactInfo.setIsCaregiver(true);
							// NOTE: had to refactor lava-crms ContactInfo to map Caregiver as an association rather than mapping caregiverId 
							// property since do not know caregiverId at this point given that new Caregiver has not been persisted. ORM will
							// take care of assigning caregiverId to ContactInfo at persistence
							caregiverContactInfo.setCaregiver(caregiver);
							caregiverContactInfo.setActive((short)1);
							if (indexContactInfoAddress != -1) {
								caregiverContactInfo.setAddress(importSetup.getDataValues()[indexContactInfoAddress]);
							}
							if (indexContactInfoCity != -1) {
								caregiverContactInfo.setCity(importSetup.getDataValues()[indexContactInfoCity]);
							}
							if (indexContactInfoState != -1) {
								caregiverContactInfo.setState(this.convertStateCode(importSetup.getDataValues()[indexContactInfoState], importLog, lineNum));
							}
							if (indexContactInfoZip != -1) {
								caregiverContactInfo.setZip(importSetup.getDataValues()[indexContactInfoZip]);
							}
							if (indexContactInfoPhone1 != -1) {
								caregiverContactInfo.setPhone1(importSetup.getDataValues()[indexContactInfoPhone1]);
							}
							if (indexContactInfoEmail != -1) {
								caregiverContactInfo.setEmail(importSetup.getDataValues()[indexContactInfoEmail]);
							}
							caregiverContactInfoCreated = true;
						}
						else {
							caregiverContactInfoCreated = false;
						}
					}
					else { 
						caregiverCreated = false;
						caregiverContactInfoCreated = false;
					}
				}
				else { 
					caregiverExisted = true;
					caregiverCreated = false;
					caregiverContactInfoCreated = false;
				}
			}
			else {
				caregiverExisted = false;
				caregiverCreated = false;
				caregiverContactInfoCreated = false;
			}
		}
	
		Map<String,Object> eventAttrMap = new HashMap<String,Object>();
		eventAttrMap.put("caregiver", caregiver);
		eventAttrMap.put("caregiverCreated", caregiverCreated);
		eventAttrMap.put("caregiverExisted", caregiverExisted);
		eventAttrMap.put("caregiverContactInfo", caregiverContactInfo);
		eventAttrMap.put("caregiverContactInfoCreated", caregiverContactInfoCreated);
		AttributeMap attributeMap = new LocalAttributeMap(eventAttrMap);
		return new Event(this, SUCCESS_FLOW_EVENT_ID, attributeMap);
	}


	/**
	 * enrollmentStatusExistsHandling
	 * 
	 * Determine whether enrollmentStatus exists or not and act accordingly based on the importDefinition settings.
	 * 
	 * @param context
	 * @param errors
	 * @param importDefinition
	 * @param importSetup
	 * @param lineNum
	 * @return SUCCESS Event if no import errors with current record; ERROR EVENT if errors
	 */
	protected Event enrollmentStatusExistsHandling(RequestContext context, BindingResult errors, 
			CrmsImportDefinition importDefinition, CrmsImportSetup importSetup,  CrmsImportLog importLog,
			int lineNum) {
		HttpServletRequest request =  ((ServletExternalContext)context.getExternalContext()).getRequest();
		LavaDaoFilter filter = EntityBase.newFilterInstance();
		SimpleDateFormat formatter;
		String dateOrTimeAsString;

		// search for existing enrollmentStatus
		EnrollmentStatus es = null;

		// if patient was just created, know that the enrollmentStatus could not exist yet, but if patient
		// was not just created, then check whether enrollmentStatus exists or not
		if (!importSetup.isPatientCreated()) { 
			filter.clearDaoParams();
			filter.setAlias("patient", "patient");
			filter.addDaoParam(filter.daoEqualityParam("patient.id", importSetup.getPatient().getId()));
			filter.addDaoParam(filter.daoEqualityParam("projName", importSetup.getRevisedProjName()));
			// note: could get the list of project enrollment statuses for the given projName and then
			// filter on a certain set of statuses, e.g. exclude 'Withdrew'. But statuses can be custom
			// for each project so that is a lot of logic and probably too much for the import definition,
			// as can generally assume that if there is data for a patient and project that the patient 
			// is currently enrolled
			try {
				es = (EnrollmentStatus) EnrollmentStatus.MANAGER.getOne(filter);
			}
			// this should never happen. if re-running import of a data file, should just be one 
			catch (IncorrectResultSizeDataAccessException ex) {
				importLog.addErrorMessage(lineNum, "Duplicate EnrollmentStatus records for patient " + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + 
						" and project " + importSetup.getRevisedProjName());
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}
		}
		
		String importEsStatus = importSetup.getIndexEsStatus() != -1 ? importSetup.getDataValues()[importSetup.getIndexEsStatus()] : importDefinition.getEsStatus();
		
		if (es == null) {
			if (importDefinition.getEsExistRule().equals(MUST_EXIST)) {
				importLog.addErrorMessage(lineNum, "Patient Enrollment does not exist for Project:" + importSetup.getRevisedProjName() + 
						" violating MUST_EXIST flag. Patient:" + importSetup.getPatient().getFullNameRevWithId());
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}else {
				// for either MUST_NOT_EXIST or MAY_OR_MAY_NOT_EXIST instantiate the Enrollment Status

				// enrollmentStatus date will typically not be supplied in the data file, so default to visitDate if not
				// note if 'patientOnlyImport' flag set in importDefinition there will not be a date to use 
				Date esDate = null;
				if (!((CrmsImportDefinition)importDefinition).getPatientOnlyImport()) {
					// note that for a patientOnlyImport, unless there is an enrollment date in the data, there will not be an enrollment
					// date to assign to the new enrollmentStatus. this means that the enrollmentStatus latestDesc / latestDate will not be set
					// because date is null
					dateOrTimeAsString = importSetup.getIndexEsStatusDate() != -1 ? importSetup.getDataValues()[importSetup.getIndexEsStatusDate()] : importSetup.getDataValues()[importSetup.getIndexVisitDate()];
					formatter = new SimpleDateFormat(importDefinition.getDateFormat() != null ? importDefinition.getDateFormat() : DEFAULT_DATE_FORMAT);
					formatter.setLenient(true); // to avoid exceptions; we check later to see if leniency was applied
					try {
						esDate = formatter.parse(dateOrTimeAsString);
					} catch (ParseException e) {
						// likely will not be called with leniency applied
						importLog.addErrorMessage(lineNum, "Enrollment Status Date or Visit Date is an invalid Date format. Date:" + dateOrTimeAsString);
						return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
					}
					// because if date format is yyyy for year part, the parser will allow any date into the future, even 5 digit dates, so 
					// have to do range checking to catch bad date errors
					java.util.Calendar esDateCalendar = java.util.Calendar.getInstance();
					esDateCalendar.setTime(esDate);
					int esDateYear = esDateCalendar.get(java.util.Calendar.YEAR);
					java.util.Calendar nowCalendar = java.util.Calendar.getInstance();
					int nowYear = nowCalendar.get(java.util.Calendar.YEAR);
					// allow for dates 5 years into the future
					if (esDateYear < (nowYear - 100) || esDateYear > (nowYear + 5)) {
						importLog.addErrorMessage(lineNum, "Enrollment Status Date or Visit Date has an invalid Year. Date:" + dateOrTimeAsString);
						return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
					}
				}
				else {
					importLog.addWarningMessage(lineNum, "Enrollment Status Date unknown for 'Patient Only Import' for:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()));
				}
					
				if (!StringUtils.hasText(importEsStatus)) {
					if (importSetup.getIndexEsStatus() != -1) {
						importLog.addErrorMessage(lineNum, "Cannot create Enrollment Status. Status field in data file (column:" + importSetup.getDataCols()[importSetup.getIndexEsStatus()] + ") has no value");
					}
					else {
						importLog.addErrorMessage(lineNum, "Cannot create Enrollment Status. No Status field supplied in data file and no value specified in definition");
					}
					return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
				}
					
				// note that non-required fields will be set in the setPropertyHandling method which iterates thru all
				// property values. this could include custom, instance specfic fields. 
						
				// create Enrollment Status record
				es = createEnrollmentStatus(importDefinition, importSetup);
				es.setPatient(importSetup.getPatient());
				es.setProjName(importSetup.getRevisedProjName());
				es.setStatus(importEsStatus, esDate);
				es.updateLatestStatusValues();
				// note that for a patientOnlyImport, unless there is an enrollment date in the data, there will not be an enrollment
				// date to assign to the new enrollmentStatus (i.e. no visit date to use). updateLatestStatusValues will not set 
				// enrollmentStatus latestDesc / latestDate if the date is null, so set it explicitly here. what will happen is that
				// on save EnrollmentStatus updateCalculatedFields will call updateLatestStatusValues which will set the latest status
				// back to null. but, if overriding the EnrollmentStatus the subclass can override updateLatestStatusValues and save
				// the status values in a local var, call the superclass updateLatestStatusValues, and if statuses are still null,
				// restore them to the saved values
				if (es.getLatestDesc() == null) {
					es.setLatestDesc(importEsStatus);
				}

				importSetup.setEnrollmentStatusCreated(true);
				importSetup.setEnrollmentStatus(es);
			}
		}
		else { // EnrollmentStatus already exists
			// warning if current status is not the same as the status defined in the import definition. 
			// e.g. if an existing EnrollmentStatus is matched with latestDesc="EXCLUDED" then user should definitely
			// be warned that data is being imported for a project from which the patient is excluded. could make
			// this an error, but there are many more common situations which do not warrant an error, e.g. lastestDesc
			// is ELIGIBLE instead of ENROLLED because perhaps a coordinator forgot to update the status
			if (es.getLatestDesc() == null) {
				importLog.addWarningMessage(lineNum, "Patient:" + importSetup.getPatient().getFullName() + " does not have a current Enrollment Status and Date for Project:" + importSetup.getRevisedProjName() + ", possibly because there is no Enrollment Status Date to import");
			}
			else if (!es.getLatestDesc().equals(importEsStatus)) {
				importLog.addWarningMessage(lineNum, "Patient:" + importSetup.getPatient().getFullName() + " has an Enrollment Status of: " + es.getLatestDesc() + " for Project:" + importSetup.getRevisedProjName() + ", not:" + importEsStatus);
			}
			
			importSetup.setEnrollmentStatusExisted(true);
			importSetup.setEnrollmentStatus(es);
			if (importDefinition.getEsExistRule().equals(MUST_NOT_EXIST)) {
				// typically with this flag the first time the import is run the Enrollment Status will not 
				// exist so it will be created above. if there were some import data errors they would be fixed
				// and the script re-imported, at which point there will be these errors for all Enrollment 
				// Statuses that were created on first run, so record will be skipped and Enrollment Status
				// will correctly not be created again
								
				// note: this differs from MAY_OR_MAY_NOT_EXIST where import of the record will continue if
				// the Enrollment Status exists (as well as if Enrollment Status does not exist as it will be 
				// created above)
				importLog.addErrorMessage(lineNum, "Enrollment Status already exists, violates Import Definition MUST_NOT_EXIST setting. Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()));
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}
		}

		return new Event(this, SUCCESS_FLOW_EVENT_ID);
	}


	
	/**
	 * visitExistHandling
	 * 
	 * Determine whether visit exists or not and act accordingly based on the importDefinition settings.
	 *  
	 * @param context
	 * @param errors
	 * @param importDefinition
	 * @param importSetup
	 * @param lineNum
	 * @return SUCCESS Event if no import errors with current record; ERROR EVENT if errors
	 */
	protected Event visitExistsHandling(RequestContext context, BindingResult errors, 
			CrmsImportDefinition importDefinition, CrmsImportSetup importSetup,  CrmsImportLog importLog,
			int lineNum) {
		HttpServletRequest request =  ((ServletExternalContext)context.getExternalContext()).getRequest();
		LavaDaoFilter filter = EntityBase.newFilterInstance();
		SimpleDateFormat formatter;
		String dateOrTimeAsString;

		// search for existing Visit
		Visit v = null;
		Date visitDate = null;
		Time visitTime = null;
		String visitType = null;

		// visitDate is required for both matching Visit and as a required field when creating new Visit
		dateOrTimeAsString = importSetup.getDataValues()[importSetup.getIndexVisitDate()];
		formatter = new SimpleDateFormat(importDefinition.getDateFormat() != null ? importDefinition.getDateFormat() : DEFAULT_DATE_FORMAT);
		formatter.setLenient(true); // to avoid exceptions; we check later to see if leniency was applied
		try {
			visitDate = formatter.parse(dateOrTimeAsString);
		} catch (ParseException e) {
			// likely will not occur with leniency applied
			importLog.addErrorMessage(lineNum, "Visit.visitDate is an invalid Date format. Date:" + dateOrTimeAsString);
			return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
		}

		// because if date format is yyyy for year part, the parser will allow any date into the future, even 5 digit dates, so 
		// have to do range checking to catch bad date errors
		java.util.Calendar visitDateCalendar = java.util.Calendar.getInstance();
		visitDateCalendar.setTime(visitDate);
		int visitDateYear = visitDateCalendar.get(java.util.Calendar.YEAR);
		java.util.Calendar nowCalendar = java.util.Calendar.getInstance();
		int nowYear = nowCalendar.get(java.util.Calendar.YEAR);
		// allow for dates 5 years into the future
		if (visitDateYear < (nowYear - 100) || visitDateYear > (nowYear + 5)) {
			importLog.addErrorMessage(lineNum, "Visit.visitDate has an invalid Year. Date:" + dateOrTimeAsString);
			return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
		}

		if (importSetup.getIndexVisitTime() != -1 && StringUtils.hasText(importSetup.getDataValues()[importSetup.getIndexVisitTime()])) {
			Date visitTimeAsDate = null;
			dateOrTimeAsString = importSetup.getDataValues()[importSetup.getIndexVisitTime()];
			formatter = new SimpleDateFormat(importDefinition.getTimeFormat() != null ? importDefinition.getTimeFormat() : DEFAULT_TIME_FORMAT);
			try{
				visitTimeAsDate = formatter.parse(dateOrTimeAsString);
				visitTime = LavaDateUtils.getTimePart(visitTimeAsDate);
			}catch (ParseException e){
				importLog.addErrorMessage(lineNum, "Visit.visitTime is an invalid Time format. Time:" + dateOrTimeAsString);
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}
		}
		
		// visitType is a required field, not null in the database, so if new Visit will be created it must be
		// either supplied in the data file or specified in the import definition (if it is accurate to assign
		// the same visitType to every visit created within the same import)
		visitType = importSetup.getIndexVisitType() != -1 ? importSetup.getDataValues()[importSetup.getIndexVisitType()] : importDefinition.getVisitType();

		
		// regardless of whether enrollmentStatus was created or not, search for a matching visit, i.e. even if the
		// enrollmentStatus was just created, there could be an existing visit for the patient that matches on date
		// that could be for a different project than what is specified in the import definition or the data file. this
		// reflects the fact that patients are often co-enrolled in multiple projects and if assessment data is to be
		// imported under some Project X for a Visit Date, need to check that the same assessment data does not exist 
		// for the Visit Date under any Project, not just Project X
		// so the DaoParam on projName is commented out below, to illustrate this point
		
		// this handles this scenario. Patient AB comes in and has a Sensory Profile - Child assessment done under
		// Project Pedi Evo Training in 2009. In 2010 the same patient has a Sensory Profile - Child which is done
		// for Project SPD WES. The export data file for Sensory Profile - Child is cumulative over time such that
		// it now contains two Sensory Profile - Child records for Patient AB: in 2009 and 2010. When importing the
		// data file in 2010 under Project SPD WES, if the Visit match included a check for Project, it would not 
		// find the Visit for SPD WES and properly create the Visit and then the Sensory Profile - Child in the Visit.
		// However, it would not find an SPD WES Visit in 2009 since that was a Pedi Evo Training Visit and it will
		// therefore erroneously create an SPD WES Visit in 2009 and import the 2009 Sensory Profile - Child data
		// which already is imported and exists under a Pedi Evo Training Visit in 2009 (on the exact same date as
		// the SPD WES Visit). By removing the Visit match on Project, this will not happen. Ignoring Project, the 
		// system will match a Visit for patient AB in 2009 that has a Sensory Profile - Child and therefore will
		// not import data that has already been imported.
		filter.clearDaoParams();
		filter.setAlias("patient", "patient");
		filter.addDaoParam(filter.daoEqualityParam("patient.id", importSetup.getPatient().getId()));
		// filter.addDaoParam(filter.daoEqualityParam("projName", importSetup.getRevisedProjName()));
		if (StringUtils.hasText(importSetup.getDataValues()[importSetup.getIndexVisitDate()])) {

			// do not have a flag for whether both date and time must match. just assume that whichever is provided should
			// match. if only date can just do a full date comparison, i.e. should not need datepart because the date in
			// the Visit is just the datepart and the date in the data file will just be a date

			// currently do not handle existing columns that have date and time in same column. not sure if this will
			// be encountered

			filter.addDaoParam(filter.daoEqualityParam("visitDate", visitDate));
			if (importSetup.getIndexVisitTime() != -1 && StringUtils.hasText(importSetup.getDataValues()[importSetup.getIndexVisitTime()])) {
				filter.addDaoParam(filter.daoEqualityParam("visitTime", visitTime));
			}
			// note: could also use daoDateAndTimeEqualityParam
			
			// visitType is optional for the search; it typically is not in generated data files, and if the import
			// is such that new Visits will not be created then it need not be specified in the definition.
			// however, without visitType, could match multiple visits. see more on this below 
			if (visitType != null) {
				if (StringUtils.hasText(visitType)) {
					filter.addDaoParam(filter.daoEqualityParam("visitType", visitType));
				}
			}
			
			filter.addDaoParam(filter.daoNot(filter.daoEqualityParam("visitStatus", "Cancelled")));
			
			try {
				v = (Visit) Visit.MANAGER.getOne(filter);
			}
			// assuming visitType is supplied, this should never happen. if re-running import of a data file, should just be one instance
			// of a given visitType on a given date
			// however, if no visitType supplied in import definition, could match multiple visits on same date, in which case
			// would not know which one to use. so user should then modify import definition to include visitType
			//TODO: when start using Visit Window for Kate, figure out what to do if matches multiple visits
			catch (IncorrectResultSizeDataAccessException ex) {
				if (visitType != null) {
					importLog.addErrorMessage(lineNum, "Duplicate Visit records for Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + 
							" and Visit Date:" + dateOrTimeAsString + " and Visit Type:" + visitType);
				}
				else {
					importLog.addErrorMessage(lineNum, "Duplicate Visit records for Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + 
							" and Visit Date:" + dateOrTimeAsString + ". Specify Visit Type in Import Definition to match on single Visit");
				}
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}
		}
		else {
			// this is not the same as Visit does not exist because do not have fields to check that the
			// Visit does or does not exist
			importLog.addErrorMessage(lineNum, "Cannot determine if Visit exists or not. Column:" + importSetup.getDataCols()[importSetup.getIndexVisitDate()] + " has no data");
			return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
		}
		
		if (v == null) {
			if (importDefinition.getVisitExistRule().equals(MUST_EXIST)) {
				importLog.addErrorMessage(lineNum, "Visit does not exist for Patient:" + importSetup.getPatient().getFullNameRev() + " Project:" + importSetup.getRevisedProjName() + " violating MUST_EXIST flag");
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}else {
				// for either MUST_NOT_EXIST or MAY_OR_MAY_NOT_EXIST instantiate the Enrollment Status

				// get fields that were not already obtained for querying for existing Visit. note that these fields are
				// required in LAVA, but they are not required in the import definition, because it may be that a single
				// visitType / visitLocation / visitWith / visitStatus does not apply to all visits that are created
				// within a single import file
				
				String visitLoc = importSetup.getIndexVisitLoc() != -1 ? importSetup.getDataValues()[importSetup.getIndexVisitLoc()] : importDefinition.getVisitLoc();
				// note there is a catch-all "Home" entry in the Visit Location list that can be used generically. could add more as needed
				if (!StringUtils.hasText(visitLoc)) {
					if (importSetup.getIndexVisitLoc() != -1) {
						importLog.addErrorMessage(lineNum, "Cannot create Visit. Visit Location field in data file (column:" + importSetup.getDataCols()[importSetup.getIndexVisitLoc()] + ") has no value");
					}
					else {
						importLog.addErrorMessage(lineNum, "Cannot create Visit. Visit Location field not supplied in data file and no value specified in definition");									
					}
					return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
				}
				
				String visitWith = importSetup.getIndexVisitWith() != -1 ? importSetup.getDataValues()[importSetup.getIndexVisitWith()] : importDefinition.getVisitWith();
				/** in case decide to require visitWith
				if (!StringUtils.hasText(visitWith)) {
					if (importSetup.getIndexVisitWith() != -1) {
						importLog.addErrorMessage(lineNum, "Cannot create Visit. Visit With field in data file (column:" + importSetup.getDataCols()[importSetup.getIndexVisitWith()] + ") has no value");
					}
					else {
						importLog.addErrorMessage(lineNum, "Cannot create Visit. Visit With field not supplied in data file and no value specified in definition");									
					}
					return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
				}
				**/

				String visitStatus = importSetup.getIndexVisitStatus() != -1 ? importSetup.getDataValues()[importSetup.getIndexVisitStatus()] : importDefinition.getVisitStatus();
				if (!StringUtils.hasText(visitStatus)) {
					if (importSetup.getIndexVisitStatus() != -1) {
						importLog.addErrorMessage(lineNum, "Cannot create Visit. Visit Status field in data file (column:" + importSetup.getDataCols()[importSetup.getIndexVisitStatus()] + ") has no value");
					}
					else {
						importLog.addErrorMessage(lineNum, "Cannot create Visit. Visit Status field not supplied in data file and no value specified in definition");									
					}
					return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
				}

				// create Visit record
				v = createVisit(importDefinition, importSetup);
				v.setPatient(importSetup.getPatient());
				v.setProjName(importSetup.getRevisedProjName());
				v.setVisitType(visitType);
				// visitDate and visitTime have already been converted above in search for Visit
				v.setVisitDate(visitDate);
				v.setVisitTime(visitTime);
				v.setVisitLocation(visitLoc);
				v.setVisitWith(visitWith);
				v.setVisitStatus(visitStatus);

				// note that non-required fields will be set in the setPropertyHandling method which iterates thru all
				// property values. this could include custom, instance specfic fields. 
						
				importSetup.setVisitCreated(true);
				importSetup.setVisit(v);
			}
		}
		else { // Visit already exists
			importSetup.setVisitExisted(true);
			importSetup.setVisit(v);
			if (importDefinition.getVisitExistRule().equals(MUST_NOT_EXIST)) {
				// typically with this flag the first time the import is run the Enrollment Status will not 
				// exist so it will be created above. if there were some import data errors they would be fixed
				// and the script re-imported, at which point there will be these errors for all Enrollment 
				// Statuses that were created on first run, so record will be skipped and Enrollment Status
				// will correctly not be created again
					
				// note: this differs from MAY_OR_MAY_NOT_EXIST where import of the record will continue if
				// the Enrollment Status exists (as well as if Enrollment Status does not exist as it will be 
				// created above)
				importLog.addErrorMessage(lineNum, "Visit already exists, violates Import Definition MUST_NOT_EXIST setting. Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()));
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}
		}

		return new Event(this, SUCCESS_FLOW_EVENT_ID);
	}
		

	
	/**
	 * insrtumentExistsHandling
	 * 
	 * Determine whether instrument exists or not and act accordingly based on the importDefinition
	 * settings. 
	 * 
	 * @param context
	 * @param errors
	 * @param importDefinition
	 * @param importSetup
	 * @param lineNum
	 * @return SUCCESS Event if no import errors with current record; ERROR EVENT if errors
	 */
	protected Event instrumentExistsHandling(RequestContext context, BindingResult errors, 
			CrmsImportDefinition importDefinition, CrmsImportSetup importSetup,  CrmsImportLog importLog,
			int lineNum) {
		
		HttpServletRequest request =  ((ServletExternalContext)context.getExternalContext()).getRequest();
		LavaDaoFilter filter = EntityBase.newFilterInstance();
		SimpleDateFormat formatter, msgDateFormatter = new SimpleDateFormat(DEFAULT_DATE_FORMAT);

		String dateOrTimeAsString;
		Map<String,Object> eventAttrMap = new HashMap<String,Object>();
		AttributeMap attributeMap = new LocalAttributeMap(eventAttrMap);
		
		// search for existing instrument
		Instrument instr = null;

		Class instrClazz =instrumentManager.getInstrumentClass(
				Instrument.getInstrTypeEncoded(importDefinition.getInstrType(), importDefinition.getInstrVer()));

		// determine dcDate for search
		// convert DCDate
		Date dcDate = null;
		// if not supplied in data file then it defaults to visit date when adding new instrument
		if (importSetup.getIndexInstrDcDate() != -1) {
			dateOrTimeAsString = importSetup.getDataValues()[importSetup.getIndexInstrDcDate()];
			formatter = new SimpleDateFormat(importDefinition.getDateFormat() != null ? importDefinition.getDateFormat() : DEFAULT_DATE_FORMAT);
			formatter.setLenient(true); // to avoid exceptions; we check later to see if leniency was applied
			try {
				dcDate = formatter.parse(dateOrTimeAsString);
			} catch (ParseException e) {
				// likely will not occur with leniency applied
				importLog.addErrorMessage(lineNum, "Instrumet.dcDate is an invalid Date format. Date:" + dateOrTimeAsString);
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}
		}
		else {
			dcDate = importSetup.getVisit().getVisitDate();
		}

		// if Visit just created, know instrument could not exist. otherwise, check if instrument exists or not
		if (!importSetup.isVisitCreated()) {
			filter.clearDaoParams();
			filter.setAlias("patient", "patient");
			filter.addDaoParam(filter.daoEqualityParam("patient.id", importSetup.getPatient().getId()));
			filter.addDaoParam(filter.daoEqualityParam("projName", importSetup.getRevisedProjName()));
			filter.setAlias("visit", "visit");
			filter.addDaoParam(filter.daoEqualityParam("visit.id", importSetup.getVisit().getId()));
			filter.addDaoParam(filter.daoEqualityParam("instrType", importDefinition.getInstrType()));
			filter.addDaoParam(filter.daoEqualityParam("dcDate", dcDate));
			try {
				instr = (Instrument) Instrument.MANAGER.getOne(instrClazz, filter);
			}
			catch (IncorrectResultSizeDataAccessException ex) {
				importLog.addErrorMessage(lineNum, "Duplicate " + importDefinition.getInstrType() + " records for Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + 
						" and Visit Date:" + msgDateFormatter.format(importSetup.getVisit().getVisitDate()));
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}
		}
		
		if (instr == null) {
			if (importDefinition.getInstrExistRule().equals(MUST_EXIST)) {
				importLog.addErrorMessage(lineNum, "Instrument does not exist violating MUST_EXIST flag. Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) 
						+ " Visit Date:" + msgDateFormatter.format(importSetup.getVisit().getVisitDate()));
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}else {
				// instrument does not exist so instantiate
				
				String dcStatus = importSetup.getIndexInstrDcStatus() != -1 ? importSetup.getDataValues()[importSetup.getIndexInstrDcStatus()] : importDefinition.getInstrDcStatus();
				if (!StringUtils.hasText(dcStatus)) {
					if (importSetup.getIndexInstrDcStatus() != -1) {
						importLog.addErrorMessage(lineNum, "Cannot create Instrument. Instrument DC Status field in data file (column:" + importSetup.getDataCols()[importSetup.getIndexInstrDcStatus()] + ") has no value");
					}
					else {
						importLog.addErrorMessage(lineNum, "Cannot create Instrument. Instrument DC Status field not supplied in data file and no value specified in definition");									
					}
					return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
				}

				try {
					instr = createInstrument(context, importDefinition, importSetup, instrClazz, dcDate, dcStatus);
					instr.setDcBy(importSetup.getVisit().getVisitWith());
					instr.setDeBy("IMPORTED");
					instr.setDeDate(new Date());
					// if import record does not have any errors then instrument will be saved so set data entry
					// status complete; if there are errors import record will be skipped and instrument will not 
					// be saved, so it is ok if setting data entry status "Complete" here
					instr.setDeStatus("Complete");
					instr.setDeNotes("Data Imported by:" + CrmsSessionUtils.getCrmsCurrentUser(sessionManager,request).getShortUserNameRev());				}
				catch (Exception ex) {
					importLog.addErrorMessage(lineNum, "Error instantiating instrument. Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) 
							+ " and Visit Date:" + msgDateFormatter.format(importSetup.getVisit().getVisitDate()));
					return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
				}
			}
			importSetup.setInstrCreated(true);
			importSetup.setInstrument(instr);
		}
		else { // instrument already exists
			importSetup.setInstrument(instr);
			if (importDefinition.getInstrExistRule().equals(MUST_NOT_EXIST)) {
				importLog.addErrorMessage(lineNum, "Instrument already exists violating Import Definition MUST_NOT_EXIST setting. Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName())
						+ " and Visit Date:" + msgDateFormatter.format(importSetup.getVisit().getVisitDate()));
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}
			else {
				// MAY_OR_MAY_NOT_EXIST or MUST_EXIST
				
				// this is where exist flag handling differs for instruments than for Patient, EnrollmentStatus, Visit.
				// need a further check in the case of instruments to make sure it does not have date entered, and if it
				// does then only proceed with import if the allowInstrUpdate flag is set in the import definition.
				// if used allow..Update flags for Patient, EnrollmentStatus and Visit, then just their mere existence
				// would be enough to consider the flag, i.e. not whether they have been data entered or not, because
				// if they exist they must have been data entered because of required fields validation. instruments
				// can be exist without being data entered.

				// note that this is also different than flags for updating Patient,EnrollmentStatus and Visit because 
				// those would not affect whether the instrument data is imported or not. so while the instrument 
				// flag can be handled at the level of the import record in terms of skipping the whole import 
				// record or not, the Patient,EnrollmentStatus and,Visit update flags would not dictate this, and so 
				// their allow..Update flags would be enforced at the individual property setting level
				
				// using deDate to determine if instrument has been data entered. not looking for a specific deStatus
				// such as 'Complete' since data entry could have any number of deStatus values
				if (instr.getDeDate() == null) {
					// in this case since the instrument has not had any data entered it is ok to use the existing instrument
					// for the import
					importSetup.setInstrExisted(true);
				}
				else {
					// CURRENTLY NOT ENABLED, i.e. user cannot set this flag in the import definition yet. waiting for
					// import to be thoroughly tested for new data before get into updating existing data
					if (importDefinition.getAllowInstrUpdate()) {
						importSetup.setInstrExistedWithData(true);
						// set an attribute on the return event so the caller can distinguish between an error and the
						// record already exists
						eventAttrMap.put("update", Boolean.TRUE);
					}
					else {
						// this is not an error in the sense that the there was a problem; rather the ERROR Event is 
						// returned so the current record will not be imported since data already exists, and it is likely
						// that a data file with this record was already imported. 
						importSetup.setInstrExistedWithData(true);
						importLog.addDebugMessage(lineNum, "Instrument exists and has already been data entered. Cannot overwrite per Import Definition. Patient:" + 
							(importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + " and Visit Date:" + msgDateFormatter.format(importSetup.getVisit().getVisitDate()));

						// set an attribute on the return event so the caller can distinguish between an error and the
						// record already exists
						eventAttrMap.put("alreadyExists", Boolean.TRUE);
						return new Event(this, ERROR_FLOW_EVENT_ID, attributeMap); // to abort processing this import record
					}
				}
			}
		}
		
		return new Event(this, SUCCESS_FLOW_EVENT_ID, attributeMap);
	}

	
	
	/**
	 * otherExistsHandling
	 * 
	 * Subclasses should override to handle additional entities beyond Patient/EnrollmentStatus/
	 * Visit/instrument.
	 * 
	 * @param context
	 * @param errors
	 * @param importDefinition
	 * @param importSetup
	 * @param lineNum
	 * @return
	 */
	protected Event otherExistsHandling(RequestContext context, BindingResult errors, 
			CrmsImportDefinition importDefinition, CrmsImportSetup importSetup, CrmsImportLog importLog, 
			int lineNum) {
		// do nothing
		return new Event(this, SUCCESS_FLOW_EVENT_ID);
	}
	
	

	
	/**
	 * Subclasses override to instantiate a custom, typically instance specific Patient subclass.
	 * 
	 * @param importDefinition
	 * @param importSetup
	 * @return
	 */
	protected Patient createPatient(CrmsImportDefinition importDefinition, CrmsImportSetup importSetup) {
		return new Patient();
	}

	
	/** 
	 * Subclasses override to instantiate a custom, typically instance specific ContactInfo subclass.
	 * 
	 * @param importDefinition
	 * @param importSetup
	 * @return
	 */
	protected ContactInfo createContactInfo(CrmsImportDefinition importDefinition, CrmsImportSetup importSetup) {
		return new ContactInfo();
	}

	
	/**
	 * Subclasses override to instantiate a custom, typically instance specific Caregiver subclass.
	 * 
	 * @param importDefinition
	 * @param importSetup
	 * @return
	 */
	protected Caregiver createCaregiver(CrmsImportDefinition importDefinition, CrmsImportSetup importSetup) {
		return new Caregiver();
	}

	
	/**
	 * Subclasses override to instantiate a custom, typically instance specific Patient subclass.
	 * 
	 * @param importDefinition
	 * @param importSetup
	 * @return
	 */
	protected EnrollmentStatus createEnrollmentStatus(CrmsImportDefinition importDefinition, CrmsImportSetup importSetup) {
		return enrollmentManager.getEnrollmentStatusPrototype(importSetup.getRevisedProjName());
	}

	
	/** 
	 * Subclasses override to instantiate a custom, typically instance specific Visit subclass.
	 * 
	 * @param importDefinition
	 * @param importSetup
	 * @return
	 */
	protected Visit createVisit(CrmsImportDefinition importDefinition, CrmsImportSetup importSetup) {
		return new Visit();
	}

	/**
	 * Subclasses override if instrument creation requires custom behavior.
	 * 
	 * @param importDefinition
	 * @param importSetup
	 * @param instrClazz
	 * @param p
	 * @param projName
	 * @param v
	 * @param instrType
	 * @param dcDate
	 * @param dcStatus
	 * @return
	 */
	protected Instrument createInstrument(RequestContext context, CrmsImportDefinition importDefinition, CrmsImportSetup importSetup, 
			Class instrClazz, Date dcDate, String dcStatus) {
		return Instrument.create(instrClazz, importSetup.getPatient(), importSetup.getVisit(), importSetup.getRevisedProjName(), 
				importDefinition.getInstrType(), dcDate, dcStatus);
	}
	

	
	/**
	 * setPropertyHandling
	 * 
	 * Iterate over all field/property values in the current data import record, setting the
	 * value on the entity property designated by the import mapping file column and property
	 * with the same column index. 
	 * 
	 * This method is about matching each data value with the entity and property on which it
	 * should be set. When the entity and property are determined then a setProperty method
	 * is called to actually set the imported data value on an entity property. 
	 * 
	 * @param context
	 * @param errors
	 * @param importDefinition
	 * @param importSetup
	 * @param importLog
	 * @param lineNum
	 * @return success Event to continue processing this record, error Event to abort processing this record 
	 */
	protected Event setPropertyHandling(RequestContext context, BindingResult errors, 
			CrmsImportDefinition importDefinition, CrmsImportSetup importSetup, CrmsImportLog importLog, int lineNum) throws Exception {
		Event returnEvent = new Event(this, SUCCESS_FLOW_EVENT_ID);
		String definitionColName, definitionPropName, definitionEntityName;
		// for error messaging
		String visitDateAsString = ((CrmsImportDefinition)importDefinition).getPatientOnlyImport() ? "" : "Visit Date:" + importSetup.getVisit().getVisitDate(); 
		
		// the primary part of an assessment import is importing the instrument variables. to make the end users job easier support
		// ignoring case on the instrument property names in the import definition mapping file. part of the implementation for that 
		// involves acquiring the set of all correct instrument property names, and that would be expensive to retrieve when processing
		// each property, so instead just retrieve it once here and pass it into setProperty for each instrument property
		Map<String,Object> instrPropNamesMap = new HashMap<String,Object>();
		try {
			if (!importDefinition.getPatientOnlyImport()) {
				instrPropNamesMap = PropertyUtils.describe(importSetup.getInstrument());
			}
		}
		catch (InvocationTargetException ex2) {
			importLog.addErrorMessage(lineNum, "[InvocationTargetException] Error on PropertyUtils.describe. Patient:" + 
					(importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + visitDateAsString);
			return new Event(this, ERROR_FLOW_EVENT_ID);
		}
		catch (IllegalAccessException ex2) {
			importLog.addErrorMessage(lineNum, "[IllegalAccessException] Error on PropertyUtils.describe. Patient:" + 
					(importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + visitDateAsString);
			return new Event(this, ERROR_FLOW_EVENT_ID);
		}
		catch (NoSuchMethodException ex2) {
			importLog.addErrorMessage(lineNum, "[NoSuchException] Error on PropertyUtils.describe. Patient:" + 
					(importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + visitDateAsString);
			return new Event(this, ERROR_FLOW_EVENT_ID);
		}

		for (int i = 0; i < importSetup.getDataValues().length; i++) {
			returnEvent = new Event(this, SUCCESS_FLOW_EVENT_ID);
			
			definitionColName = importSetup.getMappingCols()[i];
			definitionEntityName = importSetup.getMappingEntities()[i];
			definitionPropName = importSetup.getMappingProps()[i];
			
			logger.info("i="+i+" colName="+definitionColName+ " entityName="+definitionEntityName+" propName="+definitionPropName);

			// skip fields with column name or property name prefixed by XX						
			if (definitionColName.startsWith("XX") || 
				(definitionEntityName != null && definitionEntityName.startsWith("XX")) ||
				(definitionPropName != null && definitionPropName.startsWith("XX"))) { 
				// do nothing
			}

			// Set Property Values

			// instruments

			// note that if the instrument should not be updated because it has already been data entered and user
			// has specified not to overwrite in this case (in the import definition), the import record will have 
			// already been skipped and we will not get to the setting of properties
			
			// shorthand can be used in the mapping file for the first instrument specified in the 
			// importDefinition mapping file. the entity can be left blank and the first instrument will be 
			// used as the entity. this eases creation of mapping files because:
			// a) most of the time there is only one instrument in a data file so no need to populate the entity
			//    in the mapping for all instrument variables
			// b) an instrument may have many, many variables so this cuts down on what has to be set up in
			//    the mapping file for each variable
			// note that all other entities must supply the entity in the entity row, i.e. Patient, EnrollmentStatus,
			// Visit, etc. and all instruments beyond the first one

			// give this first instrument first shot at determining if the property applies to it, to support
			// this shorthand
			else if (!((CrmsImportDefinition)importDefinition).getPatientOnlyImport() && 
					(!StringUtils.hasText(definitionEntityName) || definitionEntityName.equalsIgnoreCase(importSetup.getInstrument().getInstrType()))) {

				//TODO: handle case where entity is an instrType to support multiple instrument data imports
				//mappingPropName.startsWith(..each of the importDefinition instrType (10 of them)). this could also match
				//the default instrument if mapping file puts in entity name (instrType) for it.
				//if startsWith matches, given that instrumentExistsHandler will have iterated across all importDefinition
				//instrTypes instantiating each, use the corresponding instantiated instrument (importSetup will have
				//properties instrument, instrument2, instrument3, etc. that correspond with importDefinition 
				//instrType, instrTyp2, instrType3, etc. (and don't forget about instrVer)
				
				//could go with either having mappingEntities be instrType or instrTypeEncoded. if the latter,
				//instrExistsHandling will instantiate the Instrument from which instrTypeEncoded can be obtained
				//(or it can be obtained passing importDefinition instrType to the static getInstrTypeEncoded method
				//instrTypeEncoded would be a bit off for the users. instrType just have to be careful with spaces, etc.
				//that there is an exact match

				// missing entity means shorthand to use the first instrument specified in importDefinition
				//if (!StringUtils.hasText(definitionEntityName)) {
				//	
				//}
				
				// for all instruments, if the property name is left blank in the mapping file that means that the
				// column name is the same as the property name, so there is no need to redundantly specify the property 
				// name as well.
				String propName = null;
				if (!StringUtils.hasText(definitionPropName)) {
					propName = definitionColName;
				}
				else {
					propName = definitionPropName;
				}
				
				// set property on the first instrument specified in importDefinition
				returnEvent = this.setProperty(importDefinition, importSetup, importLog, importSetup.getInstrument(), propName, i, instrPropNamesMap.keySet(), lineNum);
			}
			//Patient properties
			else if (definitionEntityName.equalsIgnoreCase("patient")) {
				// if decide to allow updates on existing Patients, then have the flags in data model to do: 
				// (importSetup.isPatientCreated() || importDefinition.getAllowPatientUpdates())
				// same for EnrollmentStatus, Visit
				
				//thinking is that should not have Patient, EnrollmentStatus, Visit allowUpdate flags because import 
				//will be used for either 
				//a) creating these if they do not exist and importing assessment data, or
				//b) importing assessment data, 
				//i.e. import is not a mechanism for updating Patient, EnrollmentStatus and Visit data
				if (importSetup.isPatientCreated()) {
					// don't need to set properties already set when Patient was created
					if (!definitionPropName.equalsIgnoreCase("firstName") && !definitionPropName.equalsIgnoreCase("lastName") && !definitionPropName.equalsIgnoreCase("birthDate")
							&& !definitionPropName.equalsIgnoreCase("gender")) {
						returnEvent = this.setProperty(importDefinition, importSetup, importLog, importSetup.getPatient(), definitionPropName, i, null, lineNum);
					}
				}
			}
			//ContactInfo properties
			else if (definitionEntityName.equalsIgnoreCase("contactInfo")) {
				if (importSetup.isContactInfoCreated()) {
					// don't need to set properties already set when ContactInfo was created
					if (!definitionPropName.equalsIgnoreCase("address") && !definitionPropName.equalsIgnoreCase("city") &&
							!definitionPropName.equalsIgnoreCase("state") && !definitionPropName.equalsIgnoreCase("zip") &&
							!definitionPropName.equalsIgnoreCase("phone1") && !definitionPropName.equalsIgnoreCase("email")) {
						returnEvent = this.setProperty(importDefinition, importSetup, importLog, importSetup.getContactInfo(), definitionPropName, i, null, lineNum);
					}
				}
			}
			//Caregiver properties
			else if (definitionEntityName.equalsIgnoreCase("caregiver")) {
				if (importSetup.isCaregiverCreated()) {
					// don't need to set properties already set when Caregiver was created
					if (!definitionPropName.equalsIgnoreCase("firstName") && !definitionPropName.equalsIgnoreCase("lastName")) {
						returnEvent = this.setProperty(importDefinition, importSetup, importLog, importSetup.getCaregiver(), definitionPropName, i, null, lineNum);
					}
				}
			}
			//Caregiver ContactInfo properties
			else if (definitionEntityName.equalsIgnoreCase("caregiverContactInfo")) {
				if (importSetup.isCaregiverContactInfoCreated()) {
					// don't need to set properties already set when Caregiver ContactInfo was created
					if (!definitionPropName.equalsIgnoreCase("address") && !definitionPropName.equalsIgnoreCase("city") &&
							!definitionPropName.equalsIgnoreCase("state") && !definitionPropName.equalsIgnoreCase("zip") &&
							!definitionPropName.equalsIgnoreCase("phone1") && !definitionPropName.equalsIgnoreCase("email")) {
						returnEvent = this.setProperty(importDefinition, importSetup, importLog, importSetup.getCaregiverContactInfo(), definitionPropName, i, null, lineNum);
					}
				}
			}
			//Caregiver2 properties
			else if (definitionEntityName.equalsIgnoreCase("caregiver2")) {
				if (importSetup.isCaregiver2Created()) {
					// don't need to set properties already set when Caregiver2 was created
					if (!definitionPropName.equalsIgnoreCase("firstName") && !definitionPropName.equalsIgnoreCase("lastName")) {
						returnEvent = this.setProperty(importDefinition, importSetup, importLog, importSetup.getCaregiver2(), definitionPropName, i, null, lineNum);
					}
				}
			}
			//Caregiver2 ContactInfo properties
			else if (definitionEntityName.equalsIgnoreCase("caregiver2ContactInfo")) {
				if (importSetup.isCaregiver2ContactInfoCreated()) {
					// don't need to set properties already set when Caregiver2 ContactInfo was created
					if (!definitionPropName.equalsIgnoreCase("address") && !definitionPropName.equalsIgnoreCase("city") &&
							!definitionPropName.equalsIgnoreCase("state") && !definitionPropName.equalsIgnoreCase("zip") &&
							!definitionPropName.equalsIgnoreCase("phone1") && !definitionPropName.equalsIgnoreCase("email")) {
						returnEvent = this.setProperty(importDefinition, importSetup, importLog, importSetup.getCaregiver2ContactInfo(), definitionPropName, i, null, lineNum);
					}
				}
			}
			//EnrollmentStatus properties
			else if (definitionEntityName.equalsIgnoreCase("enrollmentStatus")) {
				if (importSetup.isEnrollmentStatusCreated()) {
					returnEvent = this.setProperty(importDefinition, importSetup, importLog, importSetup.getEnrollmentStatus(), definitionPropName, i, null, lineNum);
				}
			}
			//Visit properties
			else if (!((CrmsImportDefinition)importDefinition).getPatientOnlyImport() && definitionEntityName.equalsIgnoreCase("visit")) {
				if (importSetup.isVisitCreated()) {
					// don't need to set properties already set when Visit was created
					if (!definitionPropName.equalsIgnoreCase("visitDate") && !definitionPropName.equalsIgnoreCase("visitType") && !definitionPropName.equalsIgnoreCase("visitLocation")
							&& !definitionPropName.equalsIgnoreCase("visitWith") && !definitionPropName.equalsIgnoreCase("visitStatus")) {
						returnEvent = this.setProperty(importDefinition, importSetup, importLog, importSetup.getVisit(), definitionPropName, i, null, lineNum);
					}
				}
			}
			else {
				// allow subclasses to set entity properties for any custom behavior
				returnEvent = setOtherPropertyHandling(importDefinition, importSetup, importLog, i, instrPropNamesMap.keySet(), lineNum);
			}
			
			// abort import of the current record if there was an error setting the imported value on the property
			if (returnEvent.getId().equals(ERROR_FLOW_EVENT_ID)) {
				return new Event(this, ERROR_FLOW_EVENT_ID); // to abort processing this import record
			}
		}
		
		return returnEvent;
	}
	

	
	/**
	 * setInstrumentCaregiver
	 *
	 * If the instrument has a caregiver (informant) property, set a caregiver on the property if the data file contains
	 * a caregiver (i.e. first and last name). The first and last name of the caregiver would either match an existing 
	 * caregiver or populate a new caregiver.
	 * 
	 * The instrument caregiver property must be named "caregiver" and must be of type Caregiver. Caregiver instruments
	 * that currently only have a caregiver id property must be refactored to also include a Caregiver property (this does
	 * not require any change to the database schema. Hibernate mapping must change to an association, Java code must have
	 * a Caregiver property and special setter code, handler must support changing Caregiver). 
	 * The reason this is required rather than just setting the caregiver id property is because if a new Caregiver has
	 * just been created by this import, the new Caregiver does has not been persisted and thus does not have a caregiver
	 * id to set on the instrument. By setting the Caregiver instead of an id, Hibernate will take care of setting 
	 * the caregiver id on the instrument when the transaction to save all of the entities created for all of the import
	 * records is committed to the database.
	 * 
	 * @param context
	 * @param errors
	 * @param importDefinition
	 * @param importSetup
	 * @param importLog
	 * @param lineNum
	 * @return success Event to continue processing this record, error Event to abort processing this record 
	 */
	protected Event setInstrumentCaregiver(RequestContext context, BindingResult errors, 
			CrmsImportDefinition importDefinition, CrmsImportSetup importSetup, CrmsImportLog importLog, int lineNum) throws Exception {
		Event returnEvent = new Event(this, SUCCESS_FLOW_EVENT_ID);

		if (importSetup.isCaregiverCreated() || importSetup.isCaregiverExisted()) {
			//TODO:when support multiple instruments in a single import, each caregiver instrument will set its caregiver property to the
			//same Caregiver, i.e. the Cargiver that was either created or matched based on a caregiver first and last name in the data file
			
			//note: if for some reason encounter a caregiver instrument where the name of the Caregiver property is not "caregiver" then 
			//could add a caregiver property name to CrmsImportDefinition
			importSetup.getInstrument().setCaregiver(importSetup.getCaregiver());
		}
		else {
			importLog.addWarningMessage(lineNum, "No Caregiver found or created to assign to instrument:" + importDefinition.getInstrType() + " for patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()));
		}
			
		return returnEvent;
	}
	
	
	
	/**
	 * Subclasses override this if setting a property involves any custom behavior.
	 * 
	 * @param importDefinition
	 * @param importSetup
	 * @param entity
	 * @param propName
	 * @param i
 	 * @param propNamesSet	populated in setPropertyHandling and any other methods that call setProperty. contains 
 	 * 						all of the property names of the entity to which propName belongs to facilitate 
 	 * 						ignoring case when matching propName to property on entity. this is only populated for 
 	 * 						instrument properties and is empty when setting a property for a non instrument entity
 	 * @param lineNum
	 * @throws Exception
	 */
	protected Event setProperty(CrmsImportDefinition importDefinition, CrmsImportSetup importSetup,	CrmsImportLog importLog, LavaEntity entity, String propName, int i, Set<String> propNamesSet, int lineNum) throws Exception {
		String entityPropName = propName; // if there is a case mismatch the entityPropName will be reset to the correct case
		boolean propIgnoreCaseMatch = false;
		// default BeanUtils converter will set empty values to a default which is not null, so change the
		// behavior so property is set to null
		// note: could just skip null values since property value is already null on new instrument, but if
		// this code were to be used for an import update then would need to set null values
		
		//TODO: consult the metadata for each property
		// 1) if the property is a string/text value then check the length of the data vs. the max string length. 
		//    add a flag to import definition about how user wants this handled: either do not import record and 
		//    create error, or truncate the string to the max length and import it and create warning
		// 2) validate data value by obtaining metadata for the entity.property, i.e. list of valid values
		
		if (!StringUtils.hasText(importSetup.getDataValues()[i])) {
			// temporarily change the conversion handling so if no value to convert, just sets property null instead of
			// throwing an exception
			// false -use a default value instead of throwing a conversion exception (for any conversions)
			// true - use null for the default value
			// -1 - array types defaulted to null
			this.getConvertUtilsBean().register(false, true, -1);
		}

		logger.info("setting propName="+propName+" to value="+importSetup.getDataValues()[i]);
		// for error messaging 
		String visitDateAsString = ((CrmsImportDefinition)importDefinition).getPatientOnlyImport() ? "" : "Visit Date:" + importSetup.getVisit().getVisitDate(); 

		try {
			// do a getSimpleProperty to determine if the mapped property name matches a property. if it does not
			// then this method will throw NoSuchMethodException. have to do this separately from setProperty because
			// BeanUtils.setProperty does not throw that exception and does not indicate anything if the property 
			// does not exist (PropertyUtils.setProperty does, but using BeanUtils.setProperty because it does
			// automatic type conversion without having to explicitly specify the type)
			Object propValue = PropertyUtils.getSimpleProperty(entity, propName);
		}
		catch (NoSuchMethodException ex) {
			// currently propNamesSet is an empty set, rather than null, when case insensitive matching should be used,
			// so this clause will never be met and will always go to the else clause
			if (propNamesSet == null) {
				// property does not exist so this is an error - and since propNamesSet not supplied not checking whether 
				// the property exists when ignoring case. this would be the case for all non-instrument properties since 
				// only doing match ignoring case for instrument properties
				importLog.addErrorMessage(lineNum, "Property name in import definition mapping file does not exist. Check spelling of property name. Property:" + propName + " Value:" + importSetup.getDataValues()[i] +  
						" Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + visitDateAsString);
				return new Event(this, ERROR_FLOW_EVENT_ID);
			}
			else {
				// if the set of property names for the entity was populated then faciliate ignoring case when matching the 
				// property name as specified in the import definition mapping file against the real property name. this is 
				// primariy for instruments since in most cases the bulk of the import will be instrument variable data, and 
				// makes it easier on those creating import definition mapping files so they don't have to be concerned with 
				// an exact match on case
				
				// if the mapped property name does not exist, before returning an error, see if it is just a case
				// mismatch and if so use the correct property name.
				
				// non-instrument entities will not populate propNamesSet so it will be empty and the following loop will 
				// not iterate and there will be an error
				for (String beanPropName : propNamesSet){
					if (propName.equalsIgnoreCase(beanPropName)) {
						entityPropName = beanPropName;
						propIgnoreCaseMatch = true;
						break;
					}
				}

				if (!propIgnoreCaseMatch) {
					importLog.addErrorMessage(lineNum, "Property name in import definition mapping file does not exist in database. Check spelling of property name. Property:" + propName + " Value:" + importSetup.getDataValues()[i] +  
						" Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + visitDateAsString);
					return new Event(this, ERROR_FLOW_EVENT_ID);
				}
				else {
					// only report warnings on the first data record because every data record uses the same mapping property names and will have
					// the same warnings
					// update: not much point in giving this warning. could potentially be many needless warning messages so commenting out for now
					//if (importDefinition.getStartDataRow().equals((short)lineNum)) {
					//	importLog.addWarningMessage(lineNum, "Value was imported, but there was a case mismatch between property name in import definition mapping file:" + propName + " and real property name:" + entityPropName + " for all rows");
					//}
				}
			}
		}

		try {
			// use Apache Commons BeanUtils rather than PropertyUtils as BeanUtils will convert the data value
			// from String to its correct type
			
			logger.info("setting propName="+propName+" to value="+importSetup.getDataValues()[i]);
			
			BeanUtils.setProperty(entity, entityPropName, importSetup.getDataValues()[i]);
		}
		catch (InvocationTargetException ex) {
			importLog.addErrorMessage(lineNum, "[InvocationTargetException] Error setting property: Property:" + entityPropName + " Value:" + importSetup.getDataValues()[i] +  
					" Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + visitDateAsString);
			return new Event(this, ERROR_FLOW_EVENT_ID);
		}
		catch (IllegalAccessException ex) {
			importLog.addErrorMessage(lineNum, "[IllegalAccessException] Error setting property: Property:" + entityPropName + " Value:" + importSetup.getDataValues()[i] +  
					" Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + visitDateAsString);
			return new Event(this, ERROR_FLOW_EVENT_ID);
		}
		catch (Exception ex) {
			importLog.addErrorMessage(lineNum, "[Exception] Error setting property: Property:" + entityPropName + " Value:" + importSetup.getDataValues()[i] +  
					" Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) + visitDateAsString);
			return new Event(this, ERROR_FLOW_EVENT_ID);
		}
		if (!StringUtils.hasText(importSetup.getDataValues()[i])) {
			// resume throwing exceptions (second and third arguments ignored in this case)
			this.setupBeanUtilConverters(importSetup);
		}								
		return new Event(this, SUCCESS_FLOW_EVENT_ID);
	}

	/**
	 * Subclasses override this to set a value on a property of an entity other than Patient,
	 * Visit, EnrollmentStatus or the instrument. 
	 * 
	 * @param importDefinition
	 * @param importSetup
	 * @param importLog
	 * @param i
	 * @param propNamesSet
	 * @param lineNum
	 * @throws Exception
	 */
	protected Event setOtherPropertyHandling(CrmsImportDefinition importDefinition, CrmsImportSetup importSetup, 
			CrmsImportLog importLog, int i, Set<String> propNamesSet, int lineNum) throws Exception {
		// if property was not set in setPropertyHandling then it is likely that there is a mapping problem, or it is
		// a custom property that a subclass should handle in an overridden setOtherPropertyHandling
		importLog.addErrorMessage(lineNum, "Property not set: Mapping column:" + importSetup.getMappingCols()[i] + " Mapping entity:" + importSetup.getMappingEntities()[i] + " Mapping property:" + importSetup.getMappingProps()[i]);
		return new Event(this, ERROR_FLOW_EVENT_ID);
	}


	/**
	 * saveImportRecord
	 * 
	 * Persist the import record to the applicable entities. 
	 * 
	 * Subclasses should override if they involve additional entities, such as ContactInfo or
	 * Caregiver, or for other custom handling. Make sure they call this superclass method. 
	 */
	protected Event saveImportRecord(CrmsImportDefinition importDefinition, CrmsImportSetup importSetup, CrmsImportLog importLog, int lineNum) {
		SimpleDateFormat formatter, msgDateFormatter = new SimpleDateFormat(DEFAULT_DATE_FORMAT);
		// for new entities must explicitly save since not associated with a Hibernate session. for
		// updates, entity was retrieved and thus attached to a session and Hibernate dirty checking should
		// implicitly update the entity

		try {
			if (importSetup.isPatientCreated()) {
				importSetup.getPatient().save();
				importLog.addCreatedMessage(lineNum, "PATIENT CREATED:" + importSetup.getPatient().getFullName());
			}
			if (importSetup.isContactInfoCreated()) {
				importSetup.getContactInfo().save();
				importLog.addCreatedMessage(lineNum, "CONTACT INFO CREATED, Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()));
			}
			if (importSetup.isCaregiverCreated()) {
				importSetup.getCaregiver().save();
				importLog.addCreatedMessage(lineNum, "CAREGIVER CREATED, Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) +
					" Caregiver:" + importSetup.getCaregiver().getFullName());
			}
			if (importSetup.isCaregiverContactInfoCreated()) {
				importSetup.getCaregiverContactInfo().save();
				importLog.addCreatedMessage(lineNum, "CAREGIVER CONTACT INFO CREATED, Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) +
					" Caregiver:" + importSetup.getCaregiver().getFullName());
			}
			if (importSetup.isCaregiver2Created()) {
				importSetup.getCaregiver2().save();
				importLog.addCreatedMessage(lineNum, "CAREGIVER CREATED, Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) +
					" Caregiver:" + importSetup.getCaregiver2().getFullName());
			}
			if (importSetup.isCaregiver2ContactInfoCreated()) {
				importSetup.getCaregiver2ContactInfo().save();
				importLog.addCreatedMessage(lineNum, "CAREGIVER CONTACT INFO CREATED, Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) +
					" Caregiver:" + importSetup.getCaregiver2().getFullName());
			}
			if (importSetup.isEnrollmentStatusCreated()) {
				importSetup.getEnrollmentStatus().save();
				importLog.addCreatedMessage(lineNum, "ENROLLMENTSTATUS CREATED, Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) +
					" Project:" + importSetup.getRevisedProjName());
			}
			if (importSetup.isVisitCreated()) {
				importSetup.getVisit().save();
				importLog.addCreatedMessage(lineNum, "VISIT CREATED, Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) +
					" VisitDate:" + msgDateFormatter.format(importSetup.getVisit().getVisitDate()) + " VisitType:" + importSetup.getVisit().getVisitType());
			}
			//TODO: when ready to allow users to overwrite data, also test on importDefinition.getAllowInstrUpdate()
			// allowInstrUpdate is used to determine whether an already existing instrument which has already
			// been data entered can be updated
			if (importSetup.isInstrCreated() || importSetup.isInstrExisted()) {
				importSetup.getInstrument().save();
				if (importSetup.isInstrCreated()) {
					importLog.addCreatedMessage(lineNum, "INSTRUMENT CREATED, Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) +
						" Instrument:" + importDefinition.getInstrType());
				}
				if (importSetup.isInstrExisted()) {
					importLog.addCreatedMessage(lineNum, "EXISTING INSTRUMENT WITH NO DATA USED, Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) +
							" Instrument:" + importDefinition.getInstrType());
				}
			}
		}
		catch (Exception e) {
			int i = 0;
			// e = InvalidDataAccessResourceException
			// e.cause = DataException
			// e.cause.cause = MysqlDataTruncation
			//  could potentially parse violating property out of cause.message e.g. "Data too long for column 'sp56_list' at row 1"
			
			// if data truncation exception iterate thru all properties, querying metadata and if property
			// style is: "suggest", "string" or "text", get the max length from the metadata and iterate thru
			// the properties comparing value against max length:
			// if user set flag to truncate and warn: truncate data that exceeds max length and create warning and try again
			// if user set flag to error out on the current record, stop processing this patient record and create error
			
			
			importLog.addErrorMessage(lineNum, "Exception on save. Could be incomplete import of this record." +
					" Patient:" + (importSetup.isPatientExisted() ? importSetup.getPatient().getFullNameWithId() : importSetup.getPatient().getFullName()) +
					"<br>Message:" + e.getMessage() +
					"<br>RootCause:" + ExceptionUtils.getRootCauseMessage(e));
			
			return new Event(this, ERROR_FLOW_EVENT_ID);
		}
		
		return new Event(this, SUCCESS_FLOW_EVENT_ID);
	}
	
	
	
	/**
	 * Called at the end of processing an import record (that was not aborted due to an error).
	 * 
	 */
	protected void updateEntityCounts(CrmsImportSetup importSetup, CrmsImportLog importLog) {
		if (importSetup.isPatientCreated()) {
			importLog.incNewPatients();
		}
		if (importSetup.isPatientExisted()) {
			importLog.incExistingPatients();
		}
		if (importSetup.isContactInfoCreated()) {
			importLog.incNewContactInfo();
		}
		if (importSetup.isContactInfoExisted()) {
			importLog.incExistingContactInfo();
		}
		if (importSetup.isCaregiverCreated()) {
			importLog.incNewCaregivers();
		}
		if (importSetup.isCaregiverExisted()) {
			importLog.incExistingCaregivers();
		}
		// note: do not keep a count of "existing" for Caregiver ContactInfo since don't check for that, i.e. Caregiver
		// ContactInfo is tightly bound to Caregiver so if Caregiver exists there is no check to see if ContactInfo exists.
		// Caregiver ContactInfo is only created when Caregiver created and there is ContactInfo data in the data file
		if (importSetup.isCaregiverContactInfoCreated()) {
			importLog.incNewCaregiverContactInfo();
		}
		// note that caregiver2 and caregiver both count together for NewCaregivers, ExistingCaregivers and NewCaregiverContactInfo
		if (importSetup.isCaregiver2Created()) {
			importLog.incNewCaregivers();
		}
		if (importSetup.isCaregiver2Existed()) {
			importLog.incExistingCaregivers();
		}
		// see comments for caregiverContactInfo above
		if (importSetup.isCaregiver2ContactInfoCreated()) {
			importLog.incNewCaregiverContactInfo();
		}
		if (importSetup.isEnrollmentStatusCreated()) {
			importLog.incNewEnrollmentStatuses();
		}
		if (importSetup.isEnrollmentStatusExisted()) {
			importLog.incExistingEnrollmentStatuses();
		}
		if (importSetup.isVisitCreated()) {
			importLog.incNewVisits();
		}
		if (importSetup.isVisitExisted()) {
			importLog.incExistingVisits();
		}
		if (importSetup.isInstrCreated()) {
			importLog.incNewInstruments();
		}
		if (importSetup.isInstrExistedWithData()) {
			importLog.incExistingInstrumentsWithData();
		}
		else if (importSetup.isInstrExisted()) {
			importLog.incExistingInstruments();
		}
	}
	
	
	/**
	 * Subclasses should override if the state property of a ContactInfo record is coded and needs to be converted
	 * to a standard two character state abbreviation.
	 * 
	 * @param stateCodeAsString
	 * @param importLog
	 * @param lineNum
	 * @return
	 */
	protected String convertStateCode(String stateCodeAsString, CrmsImportLog importLog, int lineNum) {
		return stateCodeAsString;
	}

	
	
	@Override
	public Map addReferenceData(RequestContext context, Object command,	BindingResult errors, Map model) {
		HttpServletRequest request =  ((ServletExternalContext)context.getExternalContext()).getRequest();
		//	load up dynamic lists
	 	StateDefinition state = context.getCurrentState();
		model = super.addReferenceData(context, command, errors, model); 
		Map<String,Map<String,String>> dynamicLists = getDynamicLists(model);
		CrmsImportSetup crmsImportSetup = (CrmsImportSetup) ((ComponentCommand)command).getComponents().get(this.getDefaultObjectName());
	 	if (state.getId().equals("edit")) {
			// note that this list is filtered via projectAuth filter. CrmsAuthUser getAuthDaoFilters determines the projects to
			// which a user has some kind of access. However, the list must be further filtered based on permissions to make sure 
			// the user has the import permission for each project in the list.
			Map<String,String> projList = listManager.getDynamicList(CrmsSessionUtils.getCrmsCurrentUser(sessionManager,request), "context.projectList");
			projList = CrmsAuthUtils.filterProjectListByPermission(CrmsSessionUtils.getCrmsCurrentUser(sessionManager,request),
					CoreSessionUtils.getCurrentAction(sessionManager,request), projList);
			dynamicLists.put("context.projectList", projList);
		}
		model.put("dynamicLists", dynamicLists);
		return model; 
	}

	
	
}
