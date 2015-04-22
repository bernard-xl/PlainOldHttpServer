import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import xl.net.http.HttpRequest;
import xl.net.http.HttpResponse;
import xl.net.http.HttpServer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;

/**
 * HTTP Server Test. (Helper functions are not commented.)
 */
public class HttpServerTest {

    private static HttpServer server;

    /**
     * Setup the HTTP server before any test started.
     * @throws IOException
     */
    @BeforeClass
    public static void setup() throws IOException {
        server = new HttpServer(new InetSocketAddress("127.0.0.1", 9000));
        server.handleGetOn("\\/.*", HttpServerTest::handleAllGet);
        server.handlePostOn("\\/.*", HttpServerTest::handleAllPost);
        server.handlePutOn("\\/.*", HttpServerTest::handleAllPut);
        server.handleDeleteOn("\\/.*", HttpServerTest::handleAllDelete);
        server.start();
    }

    /**
     * Shutdown the HTTP server after all tests are completed.
     */
    @AfterClass
    public static void tearDown() {
        server.shutdown();
    }

    private static String get(String paramString) throws IOException {
        URL url = new URL("http://127.0.0.1:9000?" + paramString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setRequestMethod("GET");

        connection.getResponseCode();

        return readTextResponse(connection.getInputStream());
    }

    private static String post(String paramString) throws IOException {
        URL url = new URL("http://127.0.0.1:9000");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");

        if (paramString.length() > 0) {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(paramString.getBytes());
            }
        }

        connection.getResponseCode();

        return readTextResponse(connection.getInputStream());
    }

    private static String put(String paramString) throws IOException {
        URL url = new URL("http://127.0.0.1:9000");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestMethod("PUT");

        if (paramString.length() > 0) {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(paramString.getBytes());
            }
        }

        connection.getResponseCode();

        return readTextResponse(connection.getInputStream());
    }

    private static String delete() throws IOException {
        URL url = new URL("http://127.0.0.1:9000");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setRequestMethod("DELETE");

        connection.getResponseCode();

        return readTextResponse(connection.getInputStream());
    }

    private static String readTextResponse(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static void writeTextResponse(HttpResponse response, String content) {
        ByteBuffer responseBody = ByteBuffer.wrap(content.getBytes());
        response.getHeaders().put("Content-Type", "text/plain");
        response.getHeaders().put("Content-Length", String.valueOf(responseBody.limit()));
        response.setEntity(responseBody);
    }

    private static void handleAllGet(HttpRequest request, HttpResponse response) {
        String name = request.getUrlParams().get("name");
        String greet = String.format("Hello, %s!", (name == null) ? "world" : name);
        writeTextResponse(response, greet);
    }

    private static void handleAllPost(HttpRequest request, HttpResponse response) {
        String name = request.getBodyParams().get("name");
        String greet = String.format("Hello, %s!", (name == null) ? "world" : name);
        ByteBuffer responseBody = ByteBuffer.wrap(greet.getBytes());
        writeTextResponse(response, greet);
    }

    private static void handleAllPut(HttpRequest request, HttpResponse response) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            ByteBuffer requstBody = request.getBody();
            String name = (requstBody != null) ? decoder.decode(requstBody).toString() : null;
            String greet = String.format("Hello, %s!", (name == null) ? "world" : name);
            writeTextResponse(response, greet);
        } catch (CharacterCodingException e) {
            e.printStackTrace();
        }
    }

    private static void handleAllDelete(HttpRequest request, HttpResponse response) {
        String greet = "Hello, world! is deleted";
        writeTextResponse(response, greet);
    }

    /**
     * Test GET request without parameter.
     * @throws Exception
     */
    @Test
    public void testGetWithoutParam() throws Exception {
        String response = get("");
        assert response.equals("Hello, world!");
    }

    /**
     * Test GET request with parameter.
     * @throws Exception
     */
    @Test
    public void testGetWithParam() throws Exception {
        String response = get("name=Ping");
        assert response.equals("Hello, Ping!");
    }

    /**
     * Test POST request without parameter.
     * @throws Exception
     */
    @Test
    public void testPostWithoutParam() throws Exception {
        String response = post("");
        assert response.equals("Hello, world!");
    }

    /**
     * Test POST request with parameter.
     * @throws Exception
     */
    @Test
    public void testPostWithParam() throws Exception {
        String response = post("name=Ping");
        assert response.equals("Hello, Ping!");
    }

    /**
     * Test PUT request without parameter.
     * @throws Exception
     */
    @Test
    public void testPutWithoutParam() throws Exception {
        String response = put("");
        assert response.equals("Hello, world!");
    }

    /**
     * Test PUT request with parameter.
     * @throws Exception
     */
    @Test
    public void testPutWithParam() throws Exception {
        String response = put("Bernard");
        assert response.equals("Hello, Bernard!");
    }

    /**
     * Test DELETE request.
     * @throws Exception
     */
    @Test
    public void testDelete() throws Exception {
        String response = delete();
        assert response.equals("Hello, world! is deleted");
    }
}
