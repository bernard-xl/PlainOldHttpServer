package xl.net.http;

import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulate a HTTP Request.
 * (Getters and Setters in this class is not commented as they are self-explain.)
 */
public class HttpRequest {

    private HttpMethod method;
    private URI uri;
    private String httpVersion;
    private Map<String, String> headers;
    private Map<String, String> urlParams;
    private Map<String, String> bodyParams;
    private ByteBuffer body;
    private SocketAddress requesterAddress;

    public HttpRequest() {
        headers = new HashMap<>();
        urlParams = new HashMap<>();
        bodyParams = new HashMap<>();
    }

    private static void appendToMap(Map<String, String> map, String key, String value) throws UnsupportedEncodingException {
        String existingValue = map.get(key.trim());
        if (existingValue == null) existingValue = URLDecoder.decode(value.trim(), "UTF-8");
        else existingValue += "," + URLDecoder.decode(value.trim(), "UTF-8");
        map.put(key.trim(), existingValue);
    }

    public HttpMethod getMethod() {
        return method;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getUrlParams() {
        return urlParams;
    }

    public void setUrlParams(Map<String, String> urlParams) {
        this.urlParams = urlParams;
    }

    public Map<String, String> getBodyParams() {
        return bodyParams;
    }

    public void setBodyParams(Map<String, String> bodyParams) {
        this.bodyParams = bodyParams;
    }

    public ByteBuffer getBody() {
        return body;
    }

    public void setBody(ByteBuffer body) {
        this.body = body;
    }

    public SocketAddress getRequesterAddress() {
        return requesterAddress;
    }

    public void setRequesterAddress(SocketAddress requesterAddress) {
        this.requesterAddress = requesterAddress;
    }
}
