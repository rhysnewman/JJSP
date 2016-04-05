package jjsp.http.filters;

import java.io.*;
import java.util.*;
import java.util.function.*;

import jjsp.http.*;

import static jjsp.http.HTTPResponseHeaders.*;

public class FixedResponseFilter extends AbstractRequestFilter
{
    protected String contentType;
    protected Function<HTTPResponseHeaders, String> configurator;

    public FixedResponseFilter(String name, HTTPRequestFilter chain, Function<HTTPResponseHeaders, String> configurator)
    {
        super(name, chain);
        this.configurator = configurator;
        contentType = null;
    }

    public void setContentType(String type)
    {
        contentType = type;
    }

    protected boolean handleRequest(HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
    {
        Object html = configurator.apply(response.getHeaders());
        
        String htmlContents = null;
        if (html != null)
            htmlContents = html.toString();
        if (contentType != null)
            response.getHeaders().setContentType(contentType);

        if (htmlContents == null)
            response.sendHeaders();
        else if (request.getHeaders().isHead())
        {
            response.getHeaders().setContentLength(htmlContents.length());
            response.sendHeaders();
        }
        else
            response.sendContent(htmlContents);

        return true;
    }
    
    public static FixedResponseFilter createPageNotFoundFilter(String name)
    {
        return createNotFoundContentFilter(name, null);
    }
    
    public static FixedResponseFilter createNotFoundContentFilter(String name, String notFoundContent)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configureAsNotFound(); return notFoundContent;});
    }
    
    public static FixedResponseFilter createBadRequestFilter(String name)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configureAsBadRequest(); return null;});
    }
    
    public static FixedResponseFilter createMethodNotAllowedFilter(String name)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configureAsNotAllowed(); return null;});
    }

    public static FixedResponseFilter createGatewayTimeoutFilter(String name)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configure(HTTP_GATEWAY_TIMEOUT, "Gateway Timeout"); return null;});
    }

    public static FixedResponseFilter createUnavailableFilter(String name)
    {
        return createUnavailableFilter(name, "<HTML><BODY>Service Currently Unavailable</BODY></HTML>");
    }

    public static FixedResponseFilter createUnavailableFilter(String name, String message)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configureAsUnavailable(); return message;});
    }

    public static FixedResponseFilter createSimpleServerErrorFilter(String name)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configureAsServerError(); return null;});
    }
    
    public static FixedResponseFilter createServerErrorContentFilter(String name, String errorContent)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configureAsServerError(); return errorContent;});
    }

    public static FixedResponseFilter createRedirectHandler(String name, String locationURL)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configureAsRedirect(locationURL); return "<HTML><BODY><a href=\""+locationURL+"\">Resource Moved Here</a></BODY></HTML>";});
    }

    public static FixedResponseFilter createNoContentFilter(String name)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configureAsNoContent(); return null;});
    }

    public static FixedResponseFilter createEntityTooLargeFilter(String name)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configureAsTooLarge(); return null;});
    }

    public static FixedResponseFilter createNotModifiedFilter(String name)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configureAsNotModified(); return null;});
    }

    public static FixedResponseFilter createNotAuthorizedFilter(String name)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configureAsNotAuthorised(true); headers.configureToPreventCaching(); return null;});
    }

    public static FixedResponseFilter createForbiddenFilter(String name)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configureAsForbidden(); return null;});
    }

    public static FixedResponseFilter createFilter(String name, int httpResponseCode, String httpStatusMessage, String htmlContent)
    {
        return new FixedResponseFilter(name, null, (headers) -> {headers.configure(httpResponseCode, httpStatusMessage); return htmlContent;});
    }
}
