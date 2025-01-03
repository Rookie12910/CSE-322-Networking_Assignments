import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5117;
    private static final int CHUNK_SIZE = 4096;

    public static void main(String[] args) throws IOException{
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Enter file name to upload or type 'quit' to quit: ");
            String input = scanner.nextLine();

            if (input.equalsIgnoreCase("quit")) {
                break;
            }

            String[] filePaths = input.split("\\s+");

            for (String filePath : filePaths) {
                File file = new File(filePath);
                if (file.exists() && !file.isDirectory()) {
                    Thread uploadThread = new Thread(new FileUploadTask(file));
                    uploadThread.start();
                } else {
                    System.err.println("File not found or is a directory: " + filePath);
                }
            }
        }

        scanner.close();
    }

    static class FileUploadTask implements Runnable {
        private File file;

        public FileUploadTask(File file) {

            this.file = file;
        }

        @Override
        public void run() {
            try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                 BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 FileInputStream fileInputStream = new FileInputStream(file)) {
                 String requestHeader = "UPLOAD " + file.getName() + "\r\n\r\n";
                 out.write(requestHeader.getBytes());
                 out.flush();
                 String serverMsg = in.readLine();
                 if (serverMsg.equalsIgnoreCase("Valid")) {
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int bytesRead;
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        out.flush();
                    }
                 }
                else{
                    System.out.println("Invalid file or file format, upload failed...");
                }
                fileInputStream.close();
                out.close();
                in.close();
                socket.close();
            } catch (IOException e) {
                System.err.println("Error uploading file " + file.getName() + ": " + e.getMessage());
            }
        }
    }
}

