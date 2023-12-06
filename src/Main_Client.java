import system_components.Client;

import java.io.File;
import java.io.IOException;

public class Main_Client {

    public static void main(String[] args){
        String defaultPath = String.valueOf(new File(System.getProperty("user.dir")));
        Client client = new Client(defaultPath);

        try {

            while (true) {
                System.out.println("Welcome to the file sharing system!");
                System.out.println("Use the /? command to see the list of commands.");

                while (!client.isConnected()) {
                    client.getCommand();
                    sleep(15);
                }

                while (client.isConnected()) {
                    client.clientServerInteractions();
                    sleep(15);
                }
            }
        }
        catch (IOException e) {
            System.out.println("Error occurred while starting the client: " + e.getMessage());
        }
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}