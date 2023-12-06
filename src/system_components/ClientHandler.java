package system_components;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Objects;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private String alias;
    private final Server server;
    private final DataInputStream dataInputStream;
    private final DataOutputStream dataOutputStream;
    private boolean isRegistered = false;

    private final Object fileLock = new Object();

    ClientHandler(Server server, Socket clientSocket, String alias) throws IOException {
        this.clientSocket = clientSocket;
        this.server = server;
        this.alias = alias;
        this.dataInputStream = new DataInputStream(this.clientSocket.getInputStream());
        this.dataOutputStream = new DataOutputStream(this.clientSocket.getOutputStream());
    }

    @Override
    public void run() {
        try {
            String welcomeMsg = String.format("\nConnection to the File Exchange Server is successful!\nWelcome, %s!\nUse the /? command to see the list of commands.\n",
                    alias);
            dataOutputStream.writeUTF(welcomeMsg);

            // listening loop for client's commands
            while (true) {
                try {
                    String data = dataInputStream.readUTF(); // this reads command from client
                    String[] command = data.split(" ");
                    System.out.println("User " + alias + " wants to execute " + data);
                    boolean isRunning = parseCommand(command);
                    if (!isRunning) {
                        break;
                    }
                } catch (IOException e) {
                    System.out.println("Client " + alias + " disconnected unexpectedly");
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("An error occurred during communication with client " + alias);
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }
    private void cleanup() {
        //Cleanup both client maps on the server
        this.server.clients.remove(alias);
        this.server.msgClients.remove(alias);

        try {
            if (clientSocket != null) {
                clientSocket.close();
            }
            if (dataInputStream != null) {
                dataInputStream.close();
            }
            if (dataOutputStream != null) {
                dataOutputStream.close();
            }
        } catch (IOException e) {
            System.out.println("Error occurred while closing resources for client " + alias);
            e.printStackTrace();
        }
    }


    private boolean parseCommand(String[] command) {
        try {
            switch (command[0]) {
                case "/leave":
                    handleLeave();
                    System.out.println("Command executed successfully.");
                    return false;

                case "/?":
                    handleHelp();
                    System.out.println("Command executed successfully.");
                    break;
                case "/register":
                    if (command.length != 2) {
                        dataOutputStream.writeUTF(Error.ERROR_MESSAGES.get("InvalidParameters"));
                        break;
                    }
                    handleRegister(command[1]);
                    System.out.println("Command executed successfully.");
                    break;

                case "/dir":
                    if (handleUnregistered()) {
                        break;
                    }
                    handleFilelist();
                    System.out.println("Command executed successfully.");
                    break;
                case "/userlist":
                    if (handleUnregistered()) {
                        break;
                    }
                    handleUserlist();
                    System.out.println("Command executed successfully.");
                    break;

                case "/message":
                    if (handleUnregistered()) {
                        break;
                    }
                    handleMessage(command);
                    System.out.println("Command executed successfully.");
                    break;
                case "/broadcast":
                    if (handleUnregistered()) {
                        break;
                    }
                    handle_broadcast(command);
                    System.out.println("Command executed successfully.");
                    break;
                case "/get":
                    if (handleUnregistered()) {
                        break;
                    }
                    //string build command 1 to rest and combine as file name
                    StringBuilder fileName = new StringBuilder();
                    for (int i = 1; i < command.length; i++) {
                        fileName.append(command[i]).append(" ");
                    }
                    handleGet(fileName.toString());
                    System.out.println("Command executed successfully.");
                    break;
                case "/store":
                    if (handleUnregistered()) {
                        break;
                    }
                    //string build command 1 to rest and combine as file name
                    StringBuilder storeFile = new StringBuilder();
                    for (int i = 1; i < command.length; i++) {
                        storeFile.append(command[i]).append(" ");
                    }
                    handleStore(storeFile.toString());
                    System.out.println("Command executed successfully.");
                    break;
                default:
                    dataOutputStream.writeUTF(Error.ERROR_MESSAGES.get("UnknownCommand"));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return true;
    }

    private void handleUserlist() throws IOException {
        // Show user list that is not own user and are registered (name does not contain User)
        StringBuilder userList = new StringBuilder();
        for (Map.Entry<String, Socket> entry : this.server.clients.entrySet()) {
            if (!entry.getKey().equals(this.alias) && !entry.getKey().contains("User")) {
                userList.append(entry.getKey()).append("\n");
            }
        }
        String header = "User List:\n";
        dataOutputStream.writeUTF(header + userList);
    }

    private void handleLeave() throws IOException {
        if(this.clientSocket != null && !this.clientSocket.isClosed()) {
            this.dataOutputStream.writeUTF("Connection closed. Thank you " + this.alias);
            this.clientSocket.close();
            this.server.msgClients.get(this.alias).close();
            this.server.clients.remove(this.alias);
            this.server.msgClients.remove(this.alias);
        }
    }

    private void handleRegister(String newAlias) {
        if(this.server.clients.containsKey(newAlias) || newAlias.isEmpty() || newAlias.contains("User")) {
            try {
                dataOutputStream.writeUTF(Error.ERROR_MESSAGES.get("AliasExists"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            this.server.clients.put(newAlias, this.clientSocket);
            this.server.msgClients.put(newAlias, this.server.msgClients.get(this.alias));
            this.server.clients.remove(this.alias);
            this.server.msgClients.remove(this.alias);
            this.alias = newAlias;
            this.isRegistered = true;
            try {
                System.out.println("User " + this.alias + " registered successfully.");
                dataOutputStream.writeUTF("Registration successful. Welcome " + this.alias);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleFilelist() throws IOException{
        this.server.updateFileList();
        if (this.clientSocket != null) {
            StringBuilder fileList = new StringBuilder();
            for (String file : this.server.fileList) {
                fileList.append(file).append("\n");
            }
            String header = "File List:\n";
            dataOutputStream.writeUTF(header + fileList);
        }
    }

    private void handleMessage(String[] command) throws IOException {

        String targetAlias = null;
        StringBuilder message = new StringBuilder();

        try {
            targetAlias = command[1];
            //Message can be multiple words so we need to concatenate them
            for (int i = 2; i < command.length; i++) {
                message.append(command[i]).append(" ");
            }
        }
        catch (Exception e) {
            dataOutputStream.writeUTF(Error.ERROR_MESSAGES.get("InvalidParameters"));
        }

        try {
            if (Objects.equals(targetAlias, this.alias) || targetAlias.contains("User") || message.toString().isEmpty()) {
                dataOutputStream.writeUTF(Error.ERROR_MESSAGES.get("MessageFailed"));
            }
            else if (this.server.clients.containsKey(targetAlias)) {
                this.server.msgClients.get(targetAlias).sendMsg(this.alias, message.toString());
                dataOutputStream.writeUTF("Message sent to " + targetAlias);
            }
            else {
                dataOutputStream.writeUTF("Target user not found.");
            }
        }
        catch (Exception e) {
            dataOutputStream.writeUTF(Error.ERROR_MESSAGES.get("MessageFailed"));
        }

    }

    private void handle_broadcast(String[] command) throws IOException {
        StringBuilder message = new StringBuilder();
        try {
            for (int i = 1; i < command.length; i++) {
                message.append(command[i]).append(" ");
            }
        }
        catch (Exception e) {
            dataOutputStream.writeUTF(Error.ERROR_MESSAGES.get("InvalidParameters"));
        }

        try {
            this.server.msgClients.forEach((alias, msgClient) -> {
                try {
                    msgClient.sendMsg(this.alias, message.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            dataOutputStream.writeUTF("Message sent to all users.");
        }
        catch (Exception e) {
            dataOutputStream.writeUTF(Error.ERROR_MESSAGES.get("MessageFailed"));
        }
    }

    private Boolean handleUnregistered() {
        if (!this.isRegistered) {
            try {
                dataOutputStream.writeUTF(Error.ERROR_MESSAGES.get("Unregistered"));
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private void handleStore(String fileName) {
        File file = new File(server.fileDirectory + File.separator + fileName);

        synchronized (fileLock) {
            if (file.exists()) {
                System.out.println("Error: File already exists");
                return;
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[4*1024]; // A buffer of 4KB
                int bytesReceived;
                System.out.println("Starting to receive the file " + fileName);
                while ((bytesReceived = dataInputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesReceived);
                }
                System.out.println("File has been received and saved successfully as " + fileName);
                dataOutputStream.writeUTF("File " + fileName + " successfully uploaded.");
            } catch (IOException e) {
                e.printStackTrace();
                file.delete();  // Remove the incorrectly transferred file
                System.out.println("Error occurred while transferring the file. Connection might be broken.");
            }
        }
    }

    private void handleGet(String fileName) throws IOException {
        File file = new File(server.fileDirectory + File.separator + fileName);
        if(!file.exists()){
            dataOutputStream.writeUTF(Error.ERROR_MESSAGES.get("FileNotFound"));
            return;
        }
        dataOutputStream.writeUTF("File found");

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4*1024]; // A buffer of 4KB
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dataOutputStream.write(buffer, 0, bytesRead);
            }
            dataOutputStream.writeUTF("File " + fileName + " sent to client.");
        } catch (IOException e) {
            e.printStackTrace();
            dataOutputStream.writeUTF(Error.ERROR_MESSAGES.get("FileError"));
        }
    }

    private void handleHelp() {

        String helpText = "/register <handle> - Register a unique handle or alias. Example: /register User1\n" +
                "/dir - Request directory file list from a server. Example: /dir\n" +
                "/store <filename> - Send file to server. Example: /store Hello.txt\n" +
                "/get <filename> - Fetch a file from a server. Example: /get Hello.txt\n" +
                "/leave - Disconnect from the server application. Example: /leave\n" +
                "/userlist - List all users connected to the server. Example: /userlist\n" +
                "/message <user> <message> - Send a message to a specific user. Example: /message User1 Hello!\n" +
                "/broadcast <message> - Send a message to all users. Example: /broadcast Hello!\n";
        try {
            dataOutputStream.writeUTF(helpText);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
