package xl.net.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by bernard on 7/4/15.
 */
public class HttpResponse {

    private String httpVersion;
    private HttpStatusCode statusCode;
    private Map<String, String> headers;
    private ByteBuffer entity;

    public HttpResponse(String httpVersion, HttpStatusCode statusCode) {
        this.httpVersion = httpVersion;
        this.statusCode = statusCode;
        this.headers = new LinkedHashMap<>();
        this.entity = ByteBuffer.allocate(0);
    }

    public static ByteBuffer output(HttpResponse response) {
        try {
            StringBuilder sb = new StringBuilder(50);

            String httpVersion = response.httpVersion;
            HttpStatusCode statusCode = response.statusCode;
            Map<String, String> headers = response.headers;
            ByteBuffer entity = response.entity;

            String statusLine = String.format("%s %d %s\r\n",
                    httpVersion, statusCode.getCode(), statusCode.getReasonPhrase());
            sb.append(statusLine);

            for (Map.Entry entry : headers.entrySet()) {
                String headerLine = String.format("%s: %s\r\n", entry.getKey(), entry.getValue());
                sb.append(headerLine);
            }

            sb.append("\r\n");

            byte[] headBytes = sb.toString().getBytes("UTF-8");
            ByteBuffer ret = ByteBuffer.allocate(headBytes.length + entity.limit());
            ret.put(headBytes);
            ret.put(entity);

            ret.flip();
            return ret;
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException("Failed to encode the response.", e);
        }
    }

    public void setHeader(String headerName, String content) {
        try {
            String existingValue = headers.get(headerName.trim());
            if (existingValue == null) existingValue = URLDecoder.decode(content.trim(), "UTF-8");
            else existingValue += "," + URLDecoder.decode(content.trim(), "UTF-8");
            headers.put(headerName.trim(), existingValue);
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException("Unable to encode the content.", e);
        }
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(HttpStatusCode statusCode) {
        this.statusCode = statusCode;
    }

    public ByteBuffer getEntity() {
        return entity;
    }

    public void setEntity(ByteBuffer entity) {
        this.entity = entity;
    }
}
