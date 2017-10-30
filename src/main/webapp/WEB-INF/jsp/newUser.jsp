<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<html>
<body>
    <h1>Add new user</h1>
        
    <form:form modelAttribute="form">
        <form:errors path="" element="div" />
        <div>
            <form:label path="reminder">Reminder</form:label>
            <form:input path="reminder" />
            <form:errors path="reminder" />
        </div>
        <div>
            <input type="submit" />
        </div>
    </form:form>
</body>
</html>