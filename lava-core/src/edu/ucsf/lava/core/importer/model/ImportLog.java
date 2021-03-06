package edu.ucsf.lava.core.importer.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import edu.ucsf.lava.core.file.model.ImportFile;
import edu.ucsf.lava.core.model.EntityBase;
import edu.ucsf.lava.core.model.EntityManager;

public class ImportLog extends EntityBase {
	public static EntityManager MANAGER = new EntityBase.Manager(ImportLog.class);
	
	public static String ERROR_MSG = "ERROR";
	public static String WARNING_MSG = "WARNING";
	public static String INFO_MSG = "INFO";
	public static String DEBUG_MSG = "DEBUG";
	public static String CREATED_MSG = "CREATED";
	
	private Timestamp importTimestamp;
	private String importedBy;
	private ImportFile dataFile;
	private ImportDefinition definition;
	private Integer totalRecords;
	// record imported into a new entity or entity that did not have data
	private Integer imported; 
	// record used to update existing data (only pertinent in "update" mode)							
	private Integer updated; 
	// record already existed so no import or update
	private Integer alreadyExist;
	// record not imported due to an error
	private Integer errors; 
	// record imported or updated, but with a warning
	private Integer warnings; 
	private String notes; // entered by user when doing the import
	private List<ImportLogMessage> messages = new ArrayList<ImportLogMessage>(); // warnings, errors
	// this is a non-persistent field for binding the quick filter selection
	private String activeQuickFilter;
	
	public ImportLog(){
		super();
		this.setAudited(false);
		//this.setAuditEntityType("ImportLog");
		this.totalRecords = 0;
		this.imported = 0;
		this.updated = 0;
		this.alreadyExist = 0;
		this.errors = 0;
		this.warnings = 0;
		this.importTimestamp = new Timestamp(new Date().getTime());
	}
	
	public Object[] getAssociationsToInitialize(String method) {
		if (method != null && method.equals("findById")) {
			return new Object[] {this.getMessages()};
		}
		else {
			return new Object[]{};
		}
	}
	
	public Timestamp getImportTimestamp() {
		return importTimestamp;
	}

	public void setImportTimestamp(Timestamp importTimestamp) {
		this.importTimestamp = importTimestamp;
	}

	public String getImportedBy() {
		return importedBy;
	}

	public void setImportedBy(String importedBy) {
		this.importedBy = importedBy;
	}

	public ImportFile getDataFile() {
		return dataFile;
	}

	public void setDataFile(ImportFile dataFile) {
		this.dataFile = dataFile;
	}

	public ImportDefinition getDefinition() {
		return definition;
	}

	public void setDefinition(ImportDefinition definition) {
		this.definition = definition;
	}

	public Integer getTotalRecords() {
		return totalRecords;
	}

	public void setTotalRecords(Integer totalRecords) {
		this.totalRecords = totalRecords;
	}

	public Integer getImported() {
		return imported;
	}

	public void setImported(Integer imported) {
		this.imported = imported;
	}

	public Integer getAlreadyExist() {
		return alreadyExist;
	}

	public Integer getUpdated() {
		return updated;
	}

	public void setUpdated(Integer updated) {
		this.updated = updated;
	}

	public void setAlreadyExist(Integer alreadyExist) {
		this.alreadyExist = alreadyExist;
	}

	public Integer getErrors() {
		return errors;
	}

	public void setErrors(Integer errors) {
		this.errors = errors;
	}

	public Integer getWarnings() {
		return warnings;
	}

	public void setWarnings(Integer warnings) {
		this.warnings = warnings;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public List<ImportLogMessage> getMessages() {
		return messages;
	}

	public void setMessages(List<ImportLogMessage> messages) {
		this.messages = messages;
	}
	
	public String getActiveQuickFilter() {
		return activeQuickFilter;
	}

	public void setActiveQuickFilter(String activeQuickFilter) {
		this.activeQuickFilter = activeQuickFilter;
	}

	public void addMessage(String type, Integer lineNum, String msg) {
		ImportLogMessage logMessage = new ImportLogMessage(type, lineNum, msg);
		this.messages.add(logMessage);
	}
	
	public void addDebugMessage(Integer lineNum, String msg) {
		this.addMessage(DEBUG_MSG, lineNum, msg);
	}
	
	public void addErrorMessage(Integer lineNum, String msg) {
		this.addMessage(ERROR_MSG, lineNum, msg);
		this.incErrors();
	}

	public void addWarningMessage(Integer lineNum, String msg) {
		this.addMessage(WARNING_MSG, lineNum, msg);
		this.incWarnings();
	}

	public void addInfoMessage(Integer lineNum, String msg) {
		this.addMessage(INFO_MSG, lineNum, msg);
	}
	
	public void addCreatedMessage(Integer lineNum, String msg) {
		this.addMessage(CREATED_MSG, lineNum, msg);
	}
	
	public void incTotalRecords() {
		this.totalRecords++;
	}
	
	public void incImported() {
		this.imported++;
	}

	public void incUpdated() {
		this.updated++;
	}

	public void incAlreadyExist() {
		this.alreadyExist++;
	}
	
	public void incErrors() {
		this.errors++;
	}
	
	public void incWarnings() {
		this.warnings++;
	}
	
	public String getSummaryBlock() {
		StringBuffer sb = new StringBuffer("Total=").append(this.getTotalRecords()).append(", ");
		sb.append("Imported=").append(this.getImported()).append(", ");
		// comment out until "update" is supported
		// sb.append("Updated=").append(this.getUpdated()).append(", ");
		sb.append("Already Exists=").append(this.getAlreadyExist()).append(", ");
		sb.append("Errors=").append(this.getErrors()).append(", ");
		sb.append("Warnings=").append(this.getWarnings());
		return sb.toString();
	}

}
