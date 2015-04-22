package xl.net.http;

/**
 * Callback function to handle a HTTP request, can be expressed in Lambda Expression.
 */
@FunctionalInterface
public interface HttpMethodHandler {
    public void handle(HttpRequest request, HttpResponse response);
}
