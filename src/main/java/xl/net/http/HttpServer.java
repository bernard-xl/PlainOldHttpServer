package xl.net.http;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple HTTP 1.0 Server.
 */
public class HttpServer {

    private static final Logger LOGGER = Logger.getLogger(HttpServer.class.toString());

    private static final int BUFFER_SIZE = 4096;
    private static final int RECEIVE_TIMEOUT = 10;
    private static final int SEND_TIMEOUT = 10;

    private static final String HTTP_VERSION = "HTTP/1.0";
    private static final String SERVER_NAME = "Plain Old HTTP Server";

    private AsynchronousServerSocketChannel server;
    private ExecutorService executor;
    private List<MethodHandlerEntry> getHandlers;
    private List<MethodHandlerEntry> postHandlers;
    private List<MethodHandlerEntry> putHandlers;
    private List<MethodHandlerEntry> deleteHandlers;

    /**
     * The constructor.
     *
     * @param listenAddress The listening address and port.
     * @throws IOException The specified address and port is already occupied by other program.
     */
    public HttpServer(SocketAddress listenAddress) throws IOException {
        int coreCount = Math.max(Runtime.getRuntime().availableProcessors(), 2);

        server = AsynchronousServerSocketChannel.open();
        server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        server.bind(listenAddress);

        executor = Executors.newFixedThreadPool(coreCount);

        getHandlers = new ArrayList<>();
        postHandlers = new ArrayList<>();
        putHandlers = new ArrayList<>();
        deleteHandlers = new ArrayList<>();
    }

    /**
     * Start accepting client connections.
     */
    public void start() {
        executor.submit(this::listening);
    }

    /**
     * Stop accepting any new client connection.
     * Waiting for 5 seconds for all threads to complete their response to previously connected client.
     */
    public void shutdown() {
        try {
            server.close();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e.toString());
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, e.toString());
        }
    }

    /**
     * Register a handler to handle GET HTTP method.
     *
     * @param urlPattern Regular expression to indicate the responsible path for this handler.
     * @param handler    The handler for callback when a request is received.
     */
    public void handleGetOn(String urlPattern, HttpMethodHandler handler) {
        getHandlers.add(new MethodHandlerEntry(urlPattern, handler));
    }

    /**
     * Register a handler to handle POST HTTP method.
     *
     * @param urlPattern Regular expression to indicate the responsible path for this handler.
     * @param handler    The handler for callback when a request is received.
     */
    public void handlePostOn(String urlPattern, HttpMethodHandler handler) {
        postHandlers.add(new MethodHandlerEntry(urlPattern, handler));
    }

    /**
     * Register a handler to handle PUT HTTP method.
     *
     * @param urlPattern Regular expression to indicate the responsible path for this handler.
     * @param handler    The handler for callback when a request is received.
     */
    public void handlePutOn(String urlPattern, HttpMethodHandler handler) {
        putHandlers.add(new MethodHandlerEntry(urlPattern, handler));
    }

    /**
     * Register a handler to handle DELETE HTTP method.
     *
     * @param urlPattern Regular expression to indicate the responsible path for this handler.
     * @param handler    The handler for callback when a request is received.
     */
    public void handleDeleteOn(String urlPattern, HttpMethodHandler handler) {
        deleteHandlers.add(new MethodHandlerEntry(urlPattern, handler));
    }

    /**
     * The listening thread for accept new connection.
     */
    private void listening() {
        while (!Thread.currentThread().isInterrupted() && server.isOpen()) {
            try {
                AsynchronousSocketChannel client = server.accept().get();
                executor.submit(() -> processing(client));
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.log(Level.WARNING, e.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, e.toString());
            }
        }
    }

    /**
     * Thread for processing the HTTP request.
     *
     * @param channel The connected client socket channel for response sending.
     */
    private void processing(AsynchronousSocketChannel channel) {
        try (AsynchronousSocketChannel client = channel) {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            HttpRequestDecoder requestDecoder = new HttpRequestDecoder();
            boolean requestIsComplete = false;

            while (!requestIsComplete) {
                client.read(buffer).get(RECEIVE_TIMEOUT, TimeUnit.SECONDS);
                buffer.flip();
                requestIsComplete = requestDecoder.decode(buffer);
                buffer.clear();
            }

            HttpRequest request = requestDecoder.getResult();
            HttpResponse response = new HttpResponse(HTTP_VERSION, HttpStatusCode.OK);
            List<MethodHandlerEntry> handlers;
            boolean anyHandlersMatched = false;

            request.setRequesterAddress(client.getRemoteAddress());
            response.getHeaders().put("Server", SERVER_NAME);

            switch (request.getMethod()) {
                case GET:
                    handlers = getHandlers;
                    break;
                case POST:
                    handlers = postHandlers;
                    break;
                case PUT:
                    handlers = putHandlers;
                    break;
                case DELETE:
                    handlers = deleteHandlers;
                    break;
                default:
                    handlers = null;
            }

            if (handlers != null) {
                for (MethodHandlerEntry e : handlers) {
                    Matcher matcher = e.getUrlPattern().matcher(request.getUri().toString());
                    if (matcher.matches()) {
                        try {
                            anyHandlersMatched = true;
                            e.getHandler().handle(request, response);
                            break;
                        } catch (Exception ex) {
                            response.setStatusCode(HttpStatusCode.INTERNAL_ERROR);
                            LOGGER.log(Level.WARNING, e.toString());
                        }
                    }
                }
            }

            if (!anyHandlersMatched) {
                response.setStatusCode(HttpStatusCode.NOT_FOUND);
            }

            client.write(HttpResponse.output(response)).get(SEND_TIMEOUT, TimeUnit.SECONDS);
            client.close();
        } catch (TimeoutException e) {
            LOGGER.log(Level.WARNING, e.toString());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.toString());
        }
    }

    /**
     * A HttpMethodHandler and Compiled Regular Expression Tuple.
     */
    private static class MethodHandlerEntry {
        private Pattern urlPattern;
        private HttpMethodHandler handler;

        public MethodHandlerEntry(String urlPattern, HttpMethodHandler handler) {
            this.urlPattern = Pattern.compile(urlPattern);
            this.handler = handler;
        }

        public MethodHandlerEntry(Pattern urlPattern, HttpMethodHandler handler) {
            this.urlPattern = urlPattern;
            this.handler = handler;
        }

        public Pattern getUrlPattern() {
            return urlPattern;
        }

        public void setUrlPattern(Pattern urlPattern) {
            this.urlPattern = urlPattern;
        }

        public HttpMethodHandler getHandler() {
            return handler;
        }

        public void setHandler(HttpMethodHandler handler) {
            this.handler = handler;
        }
    }
}
