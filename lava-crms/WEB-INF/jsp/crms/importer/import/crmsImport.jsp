<%@ include file="/WEB-INF/jsp/includes/include.jsp" %>

<%-- although the target of the action is 'crmsImport' must use 'import' as the component because this
action customizes the core action whose target is 'import' (see BaseFlowBuilder getBaseActionId) --%>
<c:set var="component" value="import"/>
<c:set var="viewString" value="${component}_view"/>
<c:set var="componentView" value="${requestScope[viewString]}"/>

<page:applyDecorator name="component.content">
  <page:param name="component">${component}</page:param>
  <page:param name="forceMain">true</page:param> 
  <page:param name="hasFileUpload">true</page:param>
  <page:param name="pageHeadingArgs"></page:param>

<page:applyDecorator name="component.import.content">
  	<page:param name="component">${component}</page:param>
 		<c:import url="/WEB-INF/jsp/core/importer/import/importSetupContent.jsp">
			<c:param name="component">${component}</c:param>
		</c:import>
	</page:applyDecorator>    
</page:applyDecorator>	    

