package edu.ucsf.lava.core.calendar.model;

import java.sql.Time;
import java.util.Comparator;
import java.util.Date;
import java.util.Set;

import edu.ucsf.lava.core.model.CoreEntity;
import edu.ucsf.lava.core.model.EntityBase;
import edu.ucsf.lava.core.model.EntityManager;
import edu.ucsf.lava.core.type.DateRange;
import edu.ucsf.lava.core.type.LavaDateUtils;


public class Appointment extends CoreEntity implements Comparable<Appointment> {

	public static EntityManager MANAGER = new EntityBase.Manager(Appointment.class);
	public static String APPOINTMENT_TYPE = "Appointment";
	public static String STATUS_SCHEDULED = "Scheduled";
	public static String STATUS_CANCELED = "Canceled";
	public static String STATUS_ERROR = "Error";


	protected Calendar calendar;
	protected Long calendarId;
	protected edu.ucsf.lava.core.auth.model.AuthUser organizer;
	protected Long organizerId;

	protected String type;
	protected String description;
	protected String location;
	protected Date startDate;
	protected Time startTime;
	protected Date endDate;
	protected Time endTime;
	protected String status;
	protected String notes;
	protected Time defaultDuration;

	protected Set attendees;


	public Appointment() throws Exception {
		super();
		setType(APPOINTMENT_TYPE);
		defaultDuration = LavaDateUtils.getTime("01:00:00");
	}

	public Object[] getAssociationsToInitialize(String method) {
		return new Object[]{this.calendar,this.organizer, this.attendees};
	}


	public Calendar getCalendar() {
		return calendar;
	}
	public void setCalendar(Calendar calendar) {
		this.calendar = calendar;
		if(this.calendar != null){
			this.calendarId = this.calendar.getId();
		}
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}

	public String getLocation() {
		return location;
	}
	public void setLocation(String location) {
		this.location = location;
		trackDirty("location",location);

	}
	public String getNotes() {
		return notes;
	}
	public void setNotes(String notes) {
		this.notes = notes;
	}
	public edu.ucsf.lava.core.auth.model.AuthUser getOrganizer() {
		return organizer;
	}
	public void setOrganizer(edu.ucsf.lava.core.auth.model.AuthUser owner) {
		this.organizer = owner;
		if(this.organizer != null){
			this.organizerId = this.organizer.getId();
		}
		trackDirty("organizer",organizer);
	}


	public Set getAttendees() {
		return attendees;
	}

	public void setAttendees(Set attendees) {
		this.attendees = attendees;
	}

	public Long getCalendarId() {
		return calendarId;
	}
	public void setCalendarId(Long calendarId) {
		this.calendarId = calendarId;
	}
	public Long getOrganizerId() {
		return organizerId;
	}
	public void setOrganizerId(Long ownerId) {
		this.organizerId = ownerId;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
		trackDirty("endDate",endDate);
	}

	public Time getEndTime() {
		return endTime;
	}

	public void setEndTime(Time endTime) {
		this.endTime = endTime;
		trackDirty("endTime",endTime);
	}

	public Date getStartDate() {
		return startDate;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
		trackDirty("startDate",startDate);
	}

	public Time getStartTime() {
		return startTime;

	}

	public void setStartTime(Time startTime) {
		this.startTime = startTime;
		trackDirty("startTime",startTime);
	}

	
	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public DateRange getDateRange(){
		return new DateRange(getStartDate(),getStartTime(),getEndDate(),getEndTime());
	}

	public String getDuration(){
		Long rangeMinutes = getDateRange().getRangeInMinutes();
		Long days = rangeMinutes / (60*24);
		Long remainder = rangeMinutes % (60*24);
		Long hours = remainder / 60;
		Long minutes = remainder % 60;
		StringBuffer buffer = new StringBuffer();
		if(days > 0){
			buffer.append(days).append((days > 1) ? " days " : " day ");
		}
		if(hours > 0){
			buffer.append(hours).append((hours > 1) ? " hours " : " hour ");
		}
		if(minutes > 0){
			buffer.append(minutes).append((minutes > 1) ? " minutes " : " minute ");
		}
		return buffer.toString().trim();
	}
	
	public String getShortRangeDesc(){
		return this.getDateRange().getShortRangeDesc();
	}
	
	public String getShortTimeDesc(){
		return this.getDateRange().getShortTimeDesc();
	}
	

	public Time getDefaultDuration() {
		return defaultDuration;
	}

	public void setDefaultDuration(Time defaultDuration) {
		this.defaultDuration = defaultDuration;
	}


	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public boolean isDateRangeDirty(){
		if(this.isDirty("endDate")||isDirty("endTime")||isDirty("startDate")||isDirty("startTime")){
			return true;
		}
		return false;
	}

	public void cancelAppointment() {
		this.status = Appointment.STATUS_CANCELED;
	}

	
	public Long getMinutesUntilStart(Time timeIn){
		DateRange range = getDateRange();
		if(range == null || !range.hasStart()){return null;}
		return getDateRange().getMinutesUntilStart(timeIn);
	}
	
	public Long getMinutesUntilStart(Date dateIn){
		DateRange range = getDateRange();
		if(range == null || !range.hasStart()){return null;}
		return getDateRange().getMinutesUntilStart(dateIn);
	}
	
	public Boolean isAfterStart(Date dateIn){
		DateRange range = getDateRange();
		if(range == null || !range.hasStart()){return null;}
		return getDateRange().isAfterStart(dateIn);
	}
	
	/* 
	 * Compares based on the startDate and startTime fields (natural ordering).
	 */
	public int compareTo(Appointment apptIn) {
		return Appointment.startDateTimeComparator.compare(this, apptIn);
	}
	
	/*
	 * Compares based on the startDate and startTime fields
	 * 
	 * note: this is an anonymous inner class, not a static inner class, so there
	 *       is no "class" and class name specified. valueComparator is the name 
	 *       of a static member field of the LabelValueBean class
	 */ 
	public static Comparator<Appointment> startDateTimeComparator = new Comparator<Appointment>() {
	    public int compare(Appointment appt1, Appointment appt2) {
			return DateRange.startDateComparator.compare(appt1.getDateRange(), appt2.getDateRange());   
		}
	};
	
	/*
	 * Compares based on the endDate and endTime fields
	 * 
	 * note: this is an anonymous inner class, not a static inner class, so there
	 *       is no "class" and class name specified. valueComparator is the name 
	 *       of a static member field of the LabelValueBean class
	 */ 
	public static Comparator<Appointment> endDateTimeComparator = new Comparator<Appointment>() {
	    public int compare(Appointment appt1, Appointment appt2) {
			return DateRange.endDateComparator.compare(appt1.getDateRange(), appt2.getDateRange());   
		}
	};

	

}
