package system_components;

import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;
import java.io.*;
public class Client {

    private Socket socket;
    private Socket msgSocket;
    private Boolean isConnected;
    private Boolean isRegistered;

    private String downloadPath;
    private String UploadPath;

    private String host;
    private int port;
    private Scanner scanner = new Scanner(System.in);
    private DataInputStream dis;
    private DataOutputStream dos;
    private MessageHandler messageHandler;
    public Client(String path) {
        this.downloadPath = path;
        this.UploadPath = path;
        this.isConnected = false;
        this.isRegistered = false;
    }

    public void getCommand() {
        System.out.print("Enter command: ");
        String command = scanner.nextLine();
        try {
            parseCommand(command);
        } catch (IOException e) {
            System.out.println("Error occurred while parsing the command: " + e.getMessage());
        }
    }

    public void clientServerInteractions() throws IOException {
        try {
            System.out.print("Enter command: ");
            String command = scanner.nextLine();
            dos.writeUTF(command);
            parseServerCommand(command);
        } catch (IOException e) {
            System.out.println("Error occurred while parsing the command: " + e.getMessage());
            disconnect();
        }
    }

    private void connect (String host, int port) throws IOException {
        try {
            this.socket = new Socket(host, port);
            this.msgSocket = new Socket(host, port + 1);
            this.dis = new DataInputStream(socket.getInputStream());
            this.dos = new DataOutputStream(socket.getOutputStream());
            this.isConnected = true;
            this.messageHandler = new MessageHandler(this.msgSocket);
            this.messageHandler.start();
            System.out.println(System.lineSeparator().repeat(50));
            System.out.println(dis.readUTF());
        } catch (IOException e) {
            System.out.println(Error.ERROR_MESSAGES.get("ConnectionFailed"));
            disconnect();
        }
    }

    private void disconnect() throws IOException {
        try {
            if (socket != null) {
                socket.close();
                socket = null;
            }
            if (msgSocket != null) {
                msgSocket.close();
                msgSocket = null;
            }
            if (dis != null) {
                dis.close();
                dis = null;
            }
            if (dos != null) {
                dos.close();
                dos = null;
            }
            if(messageHandler != null) {
                messageHandler.interrupt();
                messageHandler = null;
            }
            isConnected = false;
        } catch (IOException e) {
            System.out.println(Error.ERROR_MESSAGES.get("DisconnectionFailed"));
        }
    }

    private void disconnectFromServer() throws IOException {
        System.out.println(System.lineSeparator().repeat(50));
        System.out.println(dis.readUTF());
        disconnect();
    }

    private void handle_help() {
        String helpText = """
                /join <server_ip_add> <port> - Connect to the server application. Example: /join
                /exit - Exit the application. Example: /exit
                """;
        System.out.println(helpText);
    }

    public Boolean isConnected() {
        return this.isConnected;
    }

    private void exit_program() throws IOException {
        System.out.println("Thank you for using the file sharing system!");
        System.out.println("Exiting the program...");
        disconnect();
        // wait for exit input
        scanner.nextLine();
        System.exit(0);
    }

    public void get(String fileName) throws IOException {
        String serverResponse = dis.readUTF();
        if (serverResponse.contains("Error")) {
            System.out.println("File " + fileName + " not found on the server");
            return;
        }
        int fileLength = dis.readInt();
        if (fileLength == -1) {
            System.out.println("Error occurred while receiving the file");
            return;
        }
        try (FileOutputStream fos = new FileOutputStream(this.downloadPath + fileName)) {
            byte[] buffer = new byte[4 * 1024];
            int bytesRead, totalBytesReceived = 0;
            while (((bytesRead = dis.read(buffer)) != -1) && (totalBytesReceived < fileLength)) {
                fos.write(buffer, 0, bytesRead);
                totalBytesReceived += bytesRead;
            }
        }
        serverResponse = dis.readUTF();
        System.out.println(serverResponse);
    }

    public void store(String fileName) throws IOException {
        File file = new File(this.UploadPath + fileName);
        if (!file.exists()) {
            System.out.println("File " + fileName + " not found");
            dos.writeUTF("store " + fileName); // Inform server also the same message
            dos.writeInt(-1);
            return;
        }
        dos.writeUTF("store " + fileName); // Inform server about the filename
        dos.writeInt((int) file.length()); // Let server know about the file size

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4 * 1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
            }
        }

        String serverResponse = dis.readUTF();
        System.out.println(serverResponse);
    }

    private void parseCommand(String input) throws IOException {
        String[] command = input.split(" ");

        switch (command[0]) {
            case "/join" -> {
                if (command.length == 3) {
                    this.host = command[1];
                    this.port = Integer.parseInt(command[2]);
                    connect(this.host, this.port);
                } else {
                    System.out.println(Error.ERROR_MESSAGES.get("InvalidParameters"));
                }
            }
            case "/exit" -> exit_program();
            case "/?" -> handle_help();
            default -> System.out.println(Error.ERROR_MESSAGES.get("UnknownCommand"));
        }

    }

    private void parseServerCommand(String input) throws IOException {
        String[] command = input.split(" ");

        try {
            switch (command[0]) {
                case "/register" -> {
                    String response = dis.readUTF();
                    if (response.contains("success")) {
                        this.isRegistered = true;
                    }
                    System.out.println(response);
                }
                case "/get" -> {
                    if (!this.isRegistered) {
                        System.out.println(dis.readUTF());
                        break;
                    }
                    //Combine all the strings after the first index
                    String fileName = String.join(" ", Arrays.copyOfRange(command, 1, command.length));
                    get(fileName);
                }
                case "/store" -> {
                    if (!this.isRegistered) {
                        System.out.println(dis.readUTF());
                        break;
                    }
                    //Combine all the strings after the first index
                    String fileName = String.join(" ", Arrays.copyOfRange(command, 1, command.length));
                    store(fileName);
                }
                case "/leave" -> {
                    disconnectFromServer();
                }

                default -> System.out.println(dis.readUTF());
            }
        } catch (Exception e) {
            System.out.println(dis.readUTF());
        }
    }

    class MessageHandler extends  Thread {

        private final Socket msgSocket;
        private final DataInputStream dis;

        public MessageHandler(Socket msgSocket) throws IOException {
            this.msgSocket = msgSocket;
            this.dis = new DataInputStream(msgSocket.getInputStream());
        }

        @Override
        public void run() {
            while (isConnected) {
                try {
                    String msg = dis.readUTF();
                    System.out.println("\n" + msg);
                    System.out.print("Enter command: ");
                } catch (IOException e) {
                    interrupt();
                    break;
                }
            }
        }
    }

}
