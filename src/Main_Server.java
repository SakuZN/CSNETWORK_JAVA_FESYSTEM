import system_components.Server;

import java.io.File;
import java.io.IOException;

public class Main_Server {
    public static void main(String[] args) {
        File fileDir = new File("./server_files");
        if (!fileDir.exists()) {
            boolean dirCreated = fileDir.mkdirs();
            if (!dirCreated) {
                System.out.println("Error: Couldn't create directory for files");
                return;
            }
        }

        try {
            Server server = new Server(fileDir);
        } catch (IOException e) {
            System.out.println("Error occurred while starting the server: " + e.getMessage());
        }
    }
}