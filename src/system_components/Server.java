package system_components;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private final ServerSocket serverSocket;
    private final ServerSocket msgServerSocket;
    protected final Map<String, Socket> clients = new ConcurrentHashMap<>();
    protected final Map<String, MsgClient> msgClients = new ConcurrentHashMap<>();
    protected final String fileDirectory;
    protected List<String> fileList;
    protected boolean isRunning = true;

    private final ExecutorService clientHandlerExecutor = Executors.newCachedThreadPool();

    public Server(File fileDirectory) throws IOException {
        int port = 12345;
        this.fileDirectory = fileDirectory.getPath() + File.separator;
        fileList = new ArrayList<>();

        serverSocket = new ServerSocket(port);
        msgServerSocket = new ServerSocket(port + 1);

        //Client acceptor thread
        clientHandlerExecutor.execute(this::run);
        System.out.println("Server started on port " + port + " and message port " + (port + 1) + "...");
    }

    public void stop() {
        isRunning = false;
        clientHandlerExecutor.shutdown();
        clients.values().forEach(this::closeSocket);
        try {
            if(serverSocket != null) {
                serverSocket.close();
            }
            if(msgServerSocket != null) {
                msgServerSocket.close();
            }
        } catch(IOException ex) {
            ex.printStackTrace();
            System.out.println("Error occurred while closing the server sockets" + ex.getMessage());
        }
    }
    private void closeSocket(Socket socket) {
        try {
            if(socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void run() {
        while(isRunning){
            try{
                final Socket client = serverSocket.accept();
                final Socket messageClient = msgServerSocket.accept();

                String alias = "User" + clients.size();
                clients.put(alias, client);
                msgClients.put(alias, new MsgClient(messageClient));

                System.out.println("Client connected: " + alias);

                clientHandlerExecutor.submit(new ClientHandler(this, client, alias));
            } catch(SocketException ex) {
                System.out.println("Server sockets closed");
                stop();

            } catch(IOException ex) {
                ex.printStackTrace();
                System.out.println("Error occurred while accepting client connection" + ex.getMessage());
            }
        }
    }

    public static String getCurrentTime() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("<yyyy-MM-dd HH:mm:ss>");
        LocalDateTime now = LocalDateTime.now();
        return dtf.format(now);
    }

    protected void updateFileList() {
        File directory = new File(this.fileDirectory);
        String[] files = directory.list();

        if (files != null) {
            this.fileList = Arrays.asList(files);
        } else {
            this.fileList = new ArrayList<>();
        }
    }

    protected static class MsgClient {
        private final Socket msgClientSocket;
        private final DataOutputStream msgDataOutputStream;

        MsgClient(Socket msgClientSocket) throws IOException {
            this.msgClientSocket = msgClientSocket;
            this.msgDataOutputStream = new DataOutputStream(this.msgClientSocket.getOutputStream());
        }

        public void sendMsg(String sender, String msg) throws IOException {
            String message = String.format("Message from %s %s: %s", sender, getCurrentTime(), msg);
            msgDataOutputStream.writeUTF(message);
        }

        public void close() {
            try {
                if(msgClientSocket != null && !msgClientSocket.isClosed()) {
                    msgClientSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

