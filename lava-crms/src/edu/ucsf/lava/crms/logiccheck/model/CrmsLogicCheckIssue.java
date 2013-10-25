package edu.ucsf.lava.crms.logiccheck.model;

import java.util.List;

import edu.ucsf.lava.core.dao.LavaDaoFilter;
import edu.ucsf.lava.core.dao.LavaDaoParam;
import edu.ucsf.lava.core.logiccheck.controller.LogicCheckUtils;
import edu.ucsf.lava.core.logiccheck.model.LogicCheckIssue;
import edu.ucsf.lava.core.model.EntityBase;
import edu.ucsf.lava.crms.assessment.model.Instrument;
import edu.ucsf.lava.crms.assessment.model.InstrumentTracking;
import edu.ucsf.lava.crms.enrollment.model.EnrollmentStatus;
import edu.ucsf.lava.crms.model.CrmsEntity;
import edu.ucsf.lava.crms.people.model.Patient;
import edu.ucsf.lava.crms.scheduling.model.Visit;

public class CrmsLogicCheckIssue extends LogicCheckIssue {
	public static CrmsLogicCheckIssue.Manager MANAGER = new CrmsLogicCheckIssue.Manager();
	
	// only one of these entities will be used; all else NULL
	// in this way, database foreign keys could be in place
	private Patient patient;
	private EnrollmentStatus enrollmentStatus;
	private Visit visit;
	private InstrumentTracking instrumentTracking;

	private Long pidn; 
	private Long enrollStatID;
	private Long vid;
	private Long instrID;
		
	public Patient getPatient() { return patient; }
	public void setPatient(Patient patient) { this.patient = patient; }
	
	public Visit getVisit() { return visit; }
	public void setVisit(Visit visit) { this.visit = visit; }
	
	public EnrollmentStatus getEnrollmentStatus() { return enrollmentStatus; }
	public void setEnrollmentStatus(EnrollmentStatus enrollmentStatus) { this.enrollmentStatus = enrollmentStatus; }
	
	public InstrumentTracking getInstrumentTracking() { return instrumentTracking; }
	public void setInstrumentTracking(InstrumentTracking instrumentTracking) { this.instrumentTracking = instrumentTracking; }
	
	public Long getPidn() { return pidn; }
	public void setPidn(Long pidn) { this.pidn = pidn; }

	public Long getEnrollStatID() { return enrollStatID; }
	public void setEnrollStatID(Long enrollStatID) { this.enrollStatID = enrollStatID; }

	public Long getVid() { return vid; }
	public void setVid(Long vid) { this.vid = vid; }
	
	public Long getInstrID() { return instrID; }
	public void setInstrID(Long instrID) { this.instrID = instrID; }
		
	@Override
	public Long getEntityID() {
		if (this.getPidn() != null) return this.getPidn();
		if (this.getEnrollStatID() != null) return this.getEnrollStatID();
		if (this.getVid() != null) return this.getVid();
		if (this.getInstrID() != null) return this.getInstrID();
		return null;
	}
	
	@Override
	public void setEntityID(EntityBase entity) {
		// only one id can/will be non-null
		this.setPidn(null);
		this.setEnrollStatID(null);
		this.setVid(null);
		this.setInstrID(null);
		if (entity instanceof Patient)
			this.setPidn(entity.getId());
		if (entity instanceof EnrollmentStatus)
			this.setEnrollStatID(entity.getId());
		if (entity instanceof Visit)
			this.setVid(entity.getId());
		if (entity instanceof Instrument)
			this.setInstrID(entity.getId());		
	}
	
	static public class Manager extends EntityBase.Manager{
		
		public Manager(){
			super(CrmsLogicCheckIssue.class);
		}
		
		public List get(CrmsEntity entity){
			LavaDaoFilter filter = CrmsLogicCheckIssue.MANAGER.newFilterInstance();
			
			// we use instanceof, instead of polymorphism, since CrmsLogicCheckIssue actually defines these IDs
			//   in its data model.  When supporting new classes, then CrmsLogicCheckIssue would need to be updated.
			if (entity instanceof Patient)
				filter.addDaoParam(filter.daoEqualityParam("pidn", entity.getId()));
			else if (entity instanceof EnrollmentStatus)
				filter.addDaoParam(filter.daoEqualityParam("enrollStatID", entity.getId()));
			else if (entity instanceof Visit)
				filter.addDaoParam(filter.daoEqualityParam("vid", entity.getId()));
			else if (entity instanceof Instrument)
				filter.addDaoParam(filter.daoEqualityParam("instrID", entity.getId()));
			else
				return null;
			
			List<CrmsLogicCheckIssue> lcissues = (List<CrmsLogicCheckIssue>)CrmsLogicCheckIssue.MANAGER.get(filter);
			
			LogicCheckUtils.sortLogicCheckIssues(lcissues);
			return lcissues;
		}
	}
}
