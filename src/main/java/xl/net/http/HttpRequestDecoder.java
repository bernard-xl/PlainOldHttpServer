package xl.net.http;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP Request decoder that hold state for half-received request.
 */
public class HttpRequestDecoder {

    private static final byte CR = 13;
    private static final byte LF = 10;

    private State state;
    private HttpRequest request;
    private CharsetDecoder charsetDecoder;
    private String incompleteLine;
    private int entityLength;

    public HttpRequestDecoder() {
        this.state = State.REQUEST_LINE;
        this.request = new HttpRequest();
        this.charsetDecoder = StandardCharsets.UTF_8.newDecoder();
        this.incompleteLine = "";
        this.entityLength = -1;
    }

    /**
     * Decode a new received buffer, the buffer might contains incomplete HTTP request.
     * This function should be called several times with new received buffer until the decoding is completed.
     *
     * @param buffer New received buffer
     * @return Indicate if the decoding of a request is completed.
     */
    public boolean decode(ByteBuffer buffer) {

        while (buffer.hasRemaining()) {
            switch (state) {
                case REQUEST_LINE:
                    parseRequestLine(buffer);
                    break;
                case HEADERS:
                    parseHeaderLine(buffer);
                    break;
                case UNKNOWN_LENGTH_ENTITY:
                    parseUnknownLengthEntity(buffer);
                    break;
                case FIXED_LENGTH_ENTITY:
                    parseFixedLengthEntity(buffer);
                    break;
            }
        }

        if (!buffer.hasRemaining() && state == State.UNKNOWN_LENGTH_ENTITY)
            state = State.DONE;

        return state == State.DONE;
    }

    /**
     * Get the decoded HTTP request.
     *
     * @return HTTP Request object.
     */
    public HttpRequest getResult() {
        if (state != State.DONE)
            throw new IllegalStateException("The decoding has not been done yet.");
        return request;
    }

    /**
     * Reset the decoder to initial state, any in-progress decoding is discarded.
     */
    public void reset() {
        state = State.REQUEST_LINE;
        request = new HttpRequest();
    }

    private void parseRequestLine(ByteBuffer buffer) {
        try {
            String requestLine = tryReadLine(buffer);
            if (requestLine != null) {
                String[] requestLineElements = requestLine.split(" ");
                String[] uriElements = requestLineElements[1].split("\\?");
                request.setMethod(HttpMethod.valueOf(requestLineElements[0]));
                request.setUri(URI.create(uriElements[0]));
                request.setHttpVersion(requestLineElements[2]);
                if (uriElements.length == 2) parseUrlParameters(uriElements[1]);
                state = State.HEADERS;
            }
        } catch (CharacterCodingException e) {
            throw new UnsupportedOperationException("Character decoding failed.", e);
        }
    }

    private void parseHeaderLine(ByteBuffer buffer) {
        try {
            String headerLine = tryReadLine(buffer);
            if (headerLine != null) {
                if (headerLine.isEmpty()) {
                    String contentLength = request.getHeaders().get("Content-Length");
                    if (contentLength == null) {
                        state = State.UNKNOWN_LENGTH_ENTITY;
                    } else {
                        entityLength = Integer.valueOf(contentLength);
                        state = State.FIXED_LENGTH_ENTITY;
                    }
                } else {
                    Map<String, String> headers = request.getHeaders();
                    String[] headerElements = headerLine.split(":", -1);
                    appendToMap(headers, headerElements[0], headerElements[1]);
                }
            }
        } catch (UnsupportedEncodingException | CharacterCodingException e) {
            throw new UnsupportedOperationException("Character decoding failed.", e);
        }
    }

    private void parseUnknownLengthEntity(ByteBuffer buffer) {
        ByteBuffer requestBody = buffer.slice().duplicate();
        request.setBody(requestBody);
        if (request.getMethod() == HttpMethod.POST) parseBodyParameters(requestBody.slice());
        state = State.DONE;
    }

    private void parseFixedLengthEntity(ByteBuffer buffer) {
        ByteBuffer requestBody = request.getBody();
        if (requestBody == null) requestBody = ByteBuffer.allocate(entityLength);
        requestBody.put(buffer);
        buffer.position(buffer.limit());
        request.setBody(requestBody);

        if (!requestBody.hasRemaining()) {
            requestBody.flip();
            if (request.getMethod() == HttpMethod.POST) parseBodyParameters(requestBody.slice());
            state = State.DONE;
        }
    }

    private boolean parseUrlParameters(String params) {
        try {
            String[] paramsText = params.split("&");
            Map<String, String> urlParams = request.getUrlParams();

            for (String p : paramsText) {
                String[] paramParts = p.split("=", -1);
                appendToMap(urlParams, paramParts[0], paramParts[1]);
            }
            return true;
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedOperationException("Character decoding failed.", e);
        }
    }

    private boolean parseBodyParameters(ByteBuffer buffer) {
        try {
            String line = readAll(buffer);
            String[] paramsText = line.split("&");
            Map<String, String> bodyParams = request.getBodyParams();

            for (String p : paramsText) {
                String[] paramParts = p.split("=", -1);
                appendToMap(bodyParams, paramParts[0], paramParts[1]);
            }
            return true;
        } catch (UnsupportedEncodingException | CharacterCodingException e) {
            throw new UnsupportedOperationException("Character decoding failed.", e);
        }
    }

    private String tryReadLine(ByteBuffer buffer) throws CharacterCodingException {
        ByteBuffer sliced = buffer.slice();
        int crlfFound = 0;

        while (sliced.hasRemaining() && crlfFound != 2) {
            byte nextByte = sliced.get();
            if (crlfFound == 1 && nextByte == LF) crlfFound = 2;
            else if (nextByte == CR) crlfFound = 1;
            else crlfFound = 0;
        }

        int start = buffer.position();
        int end = sliced.position();
        buffer.position(start + end);

        if (crlfFound == 2) {
            sliced.limit(end - 2);
            sliced.flip();
            String line = incompleteLine + charsetDecoder.decode(sliced).toString();
            incompleteLine = "";
            return line;
        } else {
            incompleteLine = incompleteLine + charsetDecoder.decode(sliced).toString();
            return null;
        }
    }

    private String readAll(ByteBuffer buffer) throws CharacterCodingException {
        String result = charsetDecoder.decode(buffer).toString();
        buffer.position(buffer.limit());
        return result;
    }

    private void appendToMap(Map<String, String> map, String key, String value) throws UnsupportedEncodingException {
        String existingValue = map.get(key.trim());
        if (existingValue == null) existingValue = URLDecoder.decode(value.trim(), "UTF-8");
        else existingValue += " || " + URLDecoder.decode(value.trim(), "UTF-8");
        map.put(key.trim(), existingValue);
    }

    /**
     * The state of decoder, the decoder expects different upcoming content in different state.
     */
    private enum State {
        REQUEST_LINE,          //The decoder is expecting the first line of HTTP request.
        HEADERS,               //The decoder is expecting HTTP headers.
        UNKNOWN_LENGTH_ENTITY, //The decoder is expecting HTTP entity (request body) with unknown length.
        FIXED_LENGTH_ENTITY,   //The 'Content-Length' header is detected previously.
        DONE;                  //The decoding process is completed.
    }

}
