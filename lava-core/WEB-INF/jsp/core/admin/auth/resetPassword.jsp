<%@ include file="/WEB-INF/jsp/includes/include.jsp" %>


<c:set var="component" value="resetPassword"/>
<c:set var="viewString" value="${component}_view"/>
<c:set var="componentView" value="${requestScope[viewString]}"/>

<page:applyDecorator name="component.content">
  <page:param name="component">${component}</page:param>
  <page:param name="pageHeadingArgs"><tags:componentProperty component="${component}" property="user" property2="userName"/></page:param>
 
<page:applyDecorator name="component.password.content">
  <page:param name="component">${component}</page:param>
 
	<c:import url="/WEB-INF/jsp/core/admin/auth/resetPasswordContent.jsp">
		<c:param name="component">${component}</c:param>
	</c:import>
	
</page:applyDecorator>    
</page:applyDecorator>	    

