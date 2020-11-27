import java.io.*;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;

/**
 * A basic HTTP server implementation, based
 * on the example from Moodle
 * @author Thomas Buresi & Sylvain Vaure
 */
public class WebServer {

    /**
     * The port on which the server will be listening
     */
    protected static final int PORT = 3000;

    /**
     * Start the application.
     *
     * @param args Command line parameters are not used.
     */
    public static void main(String[] args) {
        WebServer ws = new WebServer();
        ws.start(PORT);
    }

    /**
     * WebServer constructor.
     *
     * @param port
     */
    protected void start(int port) {
        ServerSocket s;

        System.out.println("Webserver starting up on port " + port);
        System.out.println("(press ctrl-c to exit)");
        try {
            // create the main server socket
            s = new ServerSocket(port);
        } catch (Exception e) {
            System.out.println("Error: " + e);
            return;
        }
        System.out.println("Working Directory : " + System.getProperty("user.dir"));
        System.out.println("Waiting for connection");
        for (;;) {
            try {
                // wait for a connection
                Socket remote = s.accept();
                remote.setSoTimeout(100);
                // remote is now the connected socket
                System.out.println("Connection, sending data.");
                BufferedReader in = new BufferedReader(new InputStreamReader(
                        remote.getInputStream()));
                PrintWriter out = new PrintWriter(remote.getOutputStream());

                // read the data sent. We basically ignore it,
                // stop reading once a blank line is hit. This
                // blank line signals the end of the client HTTP
                // headers.
                String request = in.readLine();

                String str = "."; // Ignore the rest of the headers
                while (str != null && !str.equals(""))
                    str = in.readLine();

                System.err.println("Request : " + request);
                String[] requestWords = request.split(" ");
                if(requestWords.length >= 2) {
                    requestWords[1] = requestWords[1].substring(1); // Remove the leading / from Request-URI
                }
                OutputStream outputStream = remote.getOutputStream();
                InputStream inputStream = remote.getInputStream();
                try {
                    switch (requestWords[0]) {
                        case "GET":
                            handleGET(outputStream, requestWords[1]);
                            break;
                        case "HEAD":
                            handleHEAD(outputStream, requestWords[1]);
                            break;
                        case "DELETE":
                            handleDELETE(outputStream, requestWords[1]);
                            break;
                        case "POST":
                            handlePOST(outputStream, requestWords[1], in, true);
                            break;
                        case "PUT":
                            handlePOST(outputStream, requestWords[1], in, false);
                            break;
                        default:
                            sendError("501 Not Implemented", outputStream);
                            break;
                    }
                } catch (Exception e) {
                    sendError("500 Internal Server Error", outputStream);
                }
                out.flush();
                remote.close();
            } catch (Exception e) {
                System.out.println("Error: " + e);
            }
        }
    }

    /**
     * Sends the beginning of the header of a HTTP response
     * @param status The HTTP response code
     * @param out The output stream of the socket
     * @param delimiter If true, the method will signal the end of the headers
     * @throws IOException
     */
    protected void sendHeader(String status, OutputStream out, boolean delimiter) throws IOException {
        String header = "HTTP/1.0 " + status + "\r\n";
        header += "Server: Bot\r\n";
        if(delimiter) {
            header += "\r\n";
        }
        System.out.println("ANSWER HEADER :");
        System.out.println(header);
        out.write(header.getBytes());
    }

    /**
     * Sends an HTTP response headers with the specified status and file info
     *
     * @param status The HTTP status of the response
     * @param file   The file
     * @param out    The output stream to send the response to
     */
    protected void sendFileHeader(String status, File file, OutputStream out) throws IOException {
        sendHeader(status, out, false);
        String header = "Content-Type: " + Files.probeContentType(file.toPath()) + "\r\n";
        header += "Content-Length: " + file.length() + "\r\n";
        header += "\r\n";
        System.out.println("ANSWER HEADER :");
        System.out.println(header);
        out.write(header.getBytes());
    }

    /**
     * Sends an HTTP response with the specified status and the contents of the specified file
     *
     * @param status The HTTP status of the response
     * @param file   The file to be sent
     * @param out    The output stream to send the response to
     */
    protected void sendFile(String status, File file, OutputStream out) throws IOException {
        sendFileHeader(status, file, out);
        BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file));
        byte[] buffer = new byte[1024];
        int readBytes;
        while ((readBytes = fileIn.read(buffer)) != -1) {
            out.write(buffer, 0, readBytes);
        }
        fileIn.close();
    }

    /**
     * Sends an HTML error message
     * @param status The status to send
     * @param out The output stream
     * @throws IOException
     */
    protected void sendError(String status, OutputStream out) throws IOException {
        sendHeader(status, out, true);
        String htmlOutput = "<h1>" + status + "</h1>";
        out.write(htmlOutput.getBytes());
    }

    /**
     * Responds to an HTTP GET request
     * @param out The output stream to send the response to
     * @param filename The requested path
     */
    protected void handleGET(OutputStream out, String filename) throws IOException {
        System.out.println("GET : " + filename);
        if(filename.equals("")) {
            sendPermanentRedirection(out, "/bienvenue.html");
            return;
        }
        try {
            File file = new File(filename);
            if (file.exists() && file.isFile()) {
                sendFile("200 OK", file, out);
            } else {
                sendError("404 Not Found", out);
            }
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Responds to an HTTP HEAD request
     * @param out The output stream to send the response to
     * @param filename The requested path
     */
    protected void handleHEAD(OutputStream out, String filename) throws IOException {
        System.out.println("HEAD : " + filename);
        if(filename.equals("")) {
            sendPermanentRedirection(out, "/bienvenue.html");
            return;
        }
        try {
            File file = new File(filename);
            if (file.exists() && file.isFile()) {
                sendFileHeader("200 OK", file, out);
            } else {
                sendError("404 Not Found", out);
            }
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    /**
     * Responds to an HTTP DELETE request
     * @param out The output stream to send the response to
     * @param filename The requested path
     */
    protected void handleDELETE(OutputStream out, String filename) throws IOException {
        System.out.println("DELETE : " + filename);
        File file = new File(filename);
        if(file.exists()) {
            if(file.isFile()) {
                if(file.delete()) {
                    sendError("204 No Content", out);
                } else {
                    sendError("500 Internal Server Error", out);
                }
            } else {
                sendError("400 Bad Request", out);
            }
        } else {
            sendError("404 Not Found", out);
        }
    }

    /**
     * Responds to a POST or PUT request by appending the body of the request to the resource
     * @param out The output stream to send the response to
     * @param filename The requested path
     * @param in The input stream to read from
     * @param append If true, the HTTP request body is appended to the specified file, otherwise the latter is erased
     */
    protected void handlePOST(OutputStream out, String filename, BufferedReader in, boolean append) throws IOException {
        /*try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/
        File file = new File(filename);
        boolean created = !file.exists();
        FileOutputStream fileOut = new FileOutputStream(file, append);
        String read;
        do {
            try {
                read = in.readLine();
                fileOut.write(read.getBytes());
                fileOut.write(System.lineSeparator().getBytes());
            } catch(SocketTimeoutException socketTimeoutException) {
                break;
            };
        } while(true);
        fileOut.flush();
        fileOut.close();
        if(created) {
            sendError("201 Created", out);
        } else {
            sendError("200 OK", out);
        }
    }

    /**
     * Sends a 301 HTTP response with a redirection to the specified target
     * @param out The buffer to write the response to
     * @param target The target of the redirection
     * @throws IOException
     */
    protected void sendPermanentRedirection(OutputStream out, String target) throws IOException {
        sendHeader("301 Permanently Moved", out, false);
        out.write(("Location: " + target + "\r\n").getBytes());
    }
}
