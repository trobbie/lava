<%@ include file="/WEB-INF/tags/tags-include.jsp" %>

<%-- autocompleteSelect

     
--%>


<%@ attribute name="property" required="true" 
              description="the name of the field to bind to" %>
<%@ attribute name="attributesText" required="true"  
              description="attributes for the HTML element" %>
<%@ attribute name="list" type="java.util.Map" required="true" 
              description="a Map where entry key is list item value and entry value is list item label" %>
<%@ attribute name="listAttributes" 
              description="attributes which enhance the list" %>
<%@ attribute name="styleClass"
              description="[optional] the style class for the select box (or multiple classes, space separated)" %>              
<%@ attribute name="textBoxSize"
              description="[optional] size of the visible text box, in character units. defaults to 20"%>
<%@ attribute name="maxLength" 
              description="[optional] HTML 'maxlength' attribute for text boxes, i.e. the max number of characters 
              		allowed in the text box, enforced by the browser. If maxLength is not defined in the metadata,
              		defaults to 255."%>
<%@ attribute name="fieldId" required="true"  
              description="the id for the autoselect field" %> 
<%@ variable name-given="errorFlag" variable-class="java.lang.Boolean" scope="AT_END" 
              description="flag indicating property error(s) exist" %>             
<%@ variable name-given="errorMessages" variable-class="java.lang.String[]" scope="AT_END"
              description="array of error messages, if any" %>             
 

<input type="text" autocomplete="off" name="acs_textbox_${fieldId}" id="acs_textbox_${fieldId}"  class="${styleClass}" ${attributesText}
  size="${empty textBoxSize ? 20 : textBoxSize}"  maxlength="${empty maxLength ? 255 : maxLength}"
   onkeydown="return acs_object_${fieldId}.handleKeyDown(event)"
  onkeypress="return acs_object_${fieldId}.handleKeyPress(event)"
  onblur="return acs_object_${fieldId}.handleBlur(event)"
  onchange="return acs_object_${fieldId}.handleChange(event)"/>
<a  onclick="acs_object_${fieldId}.toggleList()" class="${styleClass}"><img src="images/1downarrow.png" border="0"/></a>
<spring:bind path="${property}">
   
<div id="acs_hidden_block_${fieldId}" class="acsHiddenBlock"> 
       &nbsp;
 	   <select name="${status.expression}" style="display:none" id="${fieldId}">
        
        <c:forEach items="${list}" var="entry">
            <option value="${entry.key}" 
               <c:if test="${entry.key == status.value}">selected
               		<c:set var="valueFoundInList" value="1"/>
               </c:if>>
               ${entry.value}
            </option>
        </c:forEach>
        <%-- if the entity has a value that is not in the list, add it to the list --%>
        <c:if test="${empty valueFoundInList}">
        	<%-- if the value is an instrument error code, display the corresponding error code text (this situation occurs
        		when the user has chosen to hide codes such that the code/text are not in the underlying list). look up the 
        		error code text in the missingCodesMap (only instruments have a missingCodesMap). 
        		there is one exception: when it can be determined that the property being displayed does not have codes 
        		in its list (e.g. because the property can legitimately have a negative value used by an error code) --%>
			<c:choose>
				<c:when test="${not empty missingCodesMap[status.value]}">
					<c:if test="${empty listAttributes || !fn:contains(listAttributes, 'noCodes')}">
			        	<option value="${status.value}" selected>${not empty missingCodesMap[status.value] ? missingCodesMap[status.value] : status.value}</option> 
					</c:if>
					<c:if test="${fn:contains(listAttributes, 'noCodes')}">
			        	<option value="${status.value}" selected>${status.value}</option> 
					</c:if>			
				</c:when>	       		
		       	<c:otherwise>
		        	<option value="${status.value}" selected>${status.value}</option> 
	       		</c:otherwise>	 
	       	</c:choose>
        </c:if>
        	
    </select>
</div>

	<c:set var="errorFlag" value="${status.error}"/>
    <c:set var="errorMessages" value="${status.errorMessages}"/>
</spring:bind>

<script language="javascript" type="text/javascript">
		var acs_object_${fieldId} = new acselect(document.getElementById("acs_textbox_${fieldId}"),document.getElementById("${fieldId}"));
		acs_object_${fieldId}.limitToList=0;
</script>





