import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;

public class HttpServer {
    private static final int PORT = 5117;
    public static final int CHUNK_SIZE = 4096;
    public static final String LOG_FILE = "../Log/server.log";
    public static final String UPLOAD_DIR = "../resources/uploaded";


    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT +"...");
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("client connected");
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (InputStream rawIn = clientSocket.getInputStream();
             OutputStream out = clientSocket.getOutputStream()) {
             BufferedReader in = new BufferedReader(new InputStreamReader(rawIn));
            String requestLine = in.readLine();
            if(requestLine.startsWith("GET")){
                handleGetRequest(requestLine,out);
            }
            else if(requestLine.startsWith("UPLOAD")){
                handleUploadRequest(requestLine,rawIn,out);
            }
            else {
                sendResponse(out, "400 Bad Request", "text/html", "Bad Request".getBytes(), "response: Invalid Request");
                return;
            }

            in.close();
            out.close();
            rawIn.close();
            clientSocket.close();
            System.out.println("client disconnected");

        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }


    private void handleGetRequest(String requestLine, OutputStream out) throws IOException {
        StringTokenizer tokens = new StringTokenizer(requestLine);
        tokens.nextToken();
        String filePath = tokens.nextToken();
        File file = new File("../resources" + filePath);
        logRequest("request: GET " + filePath, "200 OK");

        if (!file.exists()) {
            sendResponse(out, "404 Not Found", "text/html", "404: Page Not Found".getBytes(), "response: Page not found");
        } else if (file.isDirectory()) {
            sendDirectoryListing(out, file);
        } else {
            sendFile(out, file);
        }

    }

    private void handleUploadRequest(String requestLine, InputStream rawIn, OutputStream out) throws IOException {
        PrintWriter writer = new PrintWriter(out,true);
        StringTokenizer tokens = new StringTokenizer(requestLine);
        tokens.nextToken();
        String fileName = tokens.nextToken();
        logRequest("request: UPLOAD " + fileName, "200 OK");
        File uploadFile = new File(HttpServer.UPLOAD_DIR, fileName);
        String mimeType = Files.probeContentType(uploadFile.toPath());
        if (mimeType != null && (mimeType.startsWith("text") || mimeType.startsWith("image"))) {
            writer.println("valid");
            try (FileOutputStream fileOut = new FileOutputStream(uploadFile)) {
                byte[] buffer = new byte[HttpServer.CHUNK_SIZE];
                int bytesRead;
                while ( (bytesRead = rawIn.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, bytesRead);
                }
                fileOut.flush();
                fileOut.close();
                out.close();
                rawIn.close();
           }
            String logMessage = "response: "+ fileName +" uploaded successfully";
            logRequest(logMessage,"200 OK");

        } else {
            writer.println("Invalid");
            System.out.println("Invalid file or file format, upload failed...");
            String logMessage = "response: uploading "+ fileName +" failed";
            logRequest(logMessage,"400 Bad Request");
        }

    }

    private void sendDirectoryListing(OutputStream out, File directory) throws IOException {
        StringBuilder response = new StringBuilder();
        response.append("<html><body><h1>Directory Listing</h1><ul>");

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    response.append("<li><b><i><a href=\"").append(file.getName()).append("/\">")
                            .append(file.getName()).append("/</a></i></b></li>");
                } else {
                    response.append("<li><a href=\"").append(file.getName()).append("\">")
                            .append(file.getName()).append("</a></li>");
                }
            }
        }
        response.append("</ul></body></html>");
        sendResponse(out, "200 OK", "text/html", response.toString().getBytes(), "response: Page displayed successfully");
    }


    private void sendFile(OutputStream out, File file) throws IOException {
        String mimeType = Files.probeContentType(file.toPath());

        if (mimeType != null && mimeType.startsWith("text")) {
            String fileContent = new String(Files.readAllBytes(file.toPath()));
            String htmlResponse = "<html><body><h1>" + file.getName() + "</h1><pre>" + fileContent + "</pre></body></html>";
            sendResponse(out, "200 OK", "text/html", htmlResponse.getBytes(), "response: Text file displayed successfully");
        } else if (mimeType != null && mimeType.startsWith("image")) {
            String htmlResponse = "<html><body><h1>" + file.getName() + "</h1>" +
                    "<img src=\"data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath())) + "\" alt=\"" + file.getName() + "\">" +
                    "</body></html>";
            sendResponse(out, "200 OK", "text/html", htmlResponse.getBytes(), "response: Image displayed successfully");
        } else {
            sendDownloadResponse(out, "200 OK", file);
        }
    }

    private void sendDownloadResponse(OutputStream out, String status, File file) throws IOException {
        String mimeType = Files.probeContentType(file.toPath());
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }
        String header = "HTTP/1.0" + status + "\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Disposition: attachment; filename=\"" + file.getName() + "\"\r\n" +
                "Content-Length: " + file.length() + "\r\n\r\n";
        out.write(header.getBytes());
        try(InputStream fileInputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[HttpServer.CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        }
        String logMessage = "response: " + file.getName() + " delivered for downloading" ;
        logRequest(logMessage,"200 ok");
    }

    private void sendResponse(OutputStream out, String status, String contentType, byte[] content, String logMessage) throws IOException {
        String response = "HTTP/1.0 " + status + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + content.length + "\r\n\r\n";
        out.write(response.getBytes());
        out.write(content);
        out.flush();
        out.close();
        logRequest(logMessage,status);
    }

    private void logRequest(String logMessage, String status) {
        try (FileWriter logWriter = new FileWriter(HttpServer.LOG_FILE, true)) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            logWriter.write("[" + timestamp + "] " + logMessage + " - " + status + "\n");
        } catch (IOException e) {
            System.err.println("Error writing to log: " + e.getMessage());
        }
    }
}
