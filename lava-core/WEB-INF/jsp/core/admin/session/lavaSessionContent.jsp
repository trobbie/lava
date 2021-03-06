<%@ include file="/WEB-INF/jsp/includes/include.jsp" %>

<c:set var="component">${param.component}</c:set>


<page:applyDecorator name="component.entity.section">
  <page:param name="sectionId">session</page:param>
  <page:param name="sectionNameKey">session.session.section</page:param>
  <page:param name="subHeaderKey"></page:param>  
	
	<tags:contentColumn columnClass="colLeft2Col5050">
		<tags:createField property="httpSessionId" component="${component}" metadataName="lavaSession.id"/>
		<tags:createField property="currentStatus" component="${component}"/>
		<tags:createField property="createTimestamp" component="${component}"/>
		<tags:createField property="accessTimestamp" component="${component}"/>
		<tags:createField property="expireTimestamp" component="${component}"/>
		<tags:createField property="username" component="${component}"/>
		<tags:createField property="hostname" component="${component}"/>
	</tags:contentColumn>
	<tags:contentColumn columnClass="colRight2Col5050">
		<tags:createField property="disconnectDate,disconnectTime" component="${component}"/>
		<tags:createField property="disconnectMessage" component="${component}"/>
		<tags:createField property="notes" component="${component}"/>  
	</tags:contentColumn>
</page:applyDecorator>  
 

