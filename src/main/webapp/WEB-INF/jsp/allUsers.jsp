<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<h1>Users page</h1>

<ul>
<c:forEach items="${users}" var="user">
   <li>${user}</li>
</c:forEach>
</ul>