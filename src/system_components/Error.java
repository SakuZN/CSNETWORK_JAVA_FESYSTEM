package system_components;

import java.util.HashMap;
import java.util.Map;

public class Error {

    public static final Map<String, String> ERROR_MESSAGES = new HashMap<>();
    static {
        ERROR_MESSAGES.put("ConnectionFailed", "\nError: Connection to the Server has failed! Please check IP Address and Port Number.");
        ERROR_MESSAGES.put("DisconnectionFailed", "\nError: Disconnection failed. Please connect to the server first.");
        ERROR_MESSAGES.put("AliasExists", "\nError: Registration failed. Handle or alias already exists.");
        ERROR_MESSAGES.put("Unregistered", "\nError: You are not registered to the server. Please register first.");
        ERROR_MESSAGES.put("FileNotFound", "\nError: File not found.");
        ERROR_MESSAGES.put("FileError", "\nError: File transfer failed.");
        ERROR_MESSAGES.put("ServerFileNotFound", "\nError: File not found in the server.");
        ERROR_MESSAGES.put("UploadFailed", "\nError: Failed to upload file.");
        ERROR_MESSAGES.put("UnknownCommand", "\nError: Command not found.");
        ERROR_MESSAGES.put("InvalidParameters", "\nError: Command parameters do not match or is not allowed.");
        ERROR_MESSAGES.put("MessageFailed", "\nError: Failed to send message.");
    }
}
