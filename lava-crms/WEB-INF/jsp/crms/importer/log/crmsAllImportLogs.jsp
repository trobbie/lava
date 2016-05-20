<%@ include file="/WEB-INF/jsp/includes/include.jsp" %>

<c:set var="component" value="allImportLogs"/>

<page:applyDecorator name="component.content">
	<page:param name="component">${component}</page:param>
	<page:param name="pageHeadingArgs"></page:param>
	
<tags:outputText textKey="importLog.allImportLogs.projectContext" inline="false" styleClass="italic"/>	
	
<page:applyDecorator name="component.list.content">
	<page:param name="component">${component}</page:param>


<content tag="listColumns">
<tags:listRow>
	<tags:componentListColumnHeader component="${component}" label="&nbsp;" width="3%"/>
	<tags:componentListColumnHeader component="${component}" label="Timestamp" width="12%" sort="importTimestamp" />
	<tags:componentListColumnHeader component="${component}" label="Definition / Data File" width="50%" sort="definition.name"/>
	<tags:componentListColumnHeader component="${component}" label="Project / Summary" width="35%" sort="projName"/>
</tags:listRow>
</content>

<tags:list component="${component}" >
	<tags:listRow>
		<tags:listCell styleClass="actionButton">
			<tags:listActionURLButton buttonImage="view" actionId="lava.core.importer.log.importLog" eventId="importLog__view" idParam="${item.id}"/>	    
		</tags:listCell>
		<tags:listCell>
			<tags:listField property="importTimestamp" component="${component}" listIndex="${iterator.index}" entityType="importLog"/>
		</tags:listCell>
		<tags:listCell>
			<tags:listField property="definition.name" component="${component}" listIndex="${iterator.index}" entityType="importLog"/><br/>
			<tags:listField property="dataFile.name" component="${component}" listIndex="${iterator.index}" entityType="importLog"/>
			<%-- TODO: add download icon / action --%>			
		</tags:listCell>
		<tags:listCell>
			<tags:listField property="projName" component="${component}" listIndex="${iterator.index}" entityType="importLog"/><br/>
			<tags:listField property="summaryBlock" component="${component}" listIndex="${iterator.index}" entityType="importLog"/>
		</tags:listCell>
	</tags:listRow>
</tags:list>

</page:applyDecorator>    
</page:applyDecorator>	    

