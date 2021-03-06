<%@ include file="/WEB-INF/jsp/includes/include.jsp" %>
<html>
<body>
<!--  patient actions -->
<c:if test="${not empty currentPatient}">
<c:if test="${currentPatient.locked}">
<img src="images/lock.png"  width="10" height="10" border="0" title="LOCKED"/>&nbsp;
</c:if>

<a href="<tags:actionURL actionId="lava.crms.protocol.completion.patientCompletionStatus" idParam="${currentPatient.id}"/>">
<spring:message code="action.lava.crms.protocol.completion.patientCompletionStatus.list"/></a><br>

</c:if>

<!--  project actions -->
<a href="<tags:actionURL actionId="lava.crms.protocol.completion.projectCompletionStatus" />">
<spring:message code="action.lava.crms.protocol.completion.projectCompletionStatus.list"/></a><br>

</body>
</html>