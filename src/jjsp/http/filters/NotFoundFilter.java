package jjsp.http.filters;

import jjsp.http.*;

import java.io.IOException;
import java.util.function.Function;

public class NotFoundFilter extends AbstractRequestFilter {
    private Function generate404page;

    public NotFoundFilter(String name, Function generate404page, HTTPRequestFilter chain) {
        super(name, chain);
        this.generate404page = generate404page;
    }

    public NotFoundFilter(String name, HTTPRequestFilter chain) {
        this(name, null, chain);
    }

    private String default404Page(String path) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE html>");
        builder.append("<html>");
        builder.append("<body>");

        builder.append("<h1>404 Page Not Found</h1>");
        builder.append("<p>");
        builder.append("Failed to find '").append(path).append("'");
        builder.append("</p>");

        builder.append("</body>");
        builder.append("</html>");

        return builder.toString();
    }

    private boolean respondWithPage(HTTPRequestHeaders requestHeaders) {
        if ( requestHeaders.isHead() || requestHeaders.isPost() )
            return false;

        if ( requestHeaders.isGet() ) {
            String path = requestHeaders.getPath().toLowerCase();
            if ( path.contains(".") ) {
                return path.endsWith(".html") || path.endsWith(".htm");
            }
            else
                return true;
        }
        return false;
    }

    @Override
    protected boolean handleRequest(HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException {
        String path = request.getHeaders().getPath();

        response.getHeaders().configureAsNotFound();
        if ( respondWithPage(request.getHeaders()) ) {
            // returns a full 404 page
            String html;
            if (generate404page != null)
                html = (String) generate404page.apply(path);
            else
                html = default404Page(path);
            response.sendHTML(html);
        }
        else // all other case, e.g. missing files etc, returns empty content
            response.sendHeaders();

        return true;
    }
}
