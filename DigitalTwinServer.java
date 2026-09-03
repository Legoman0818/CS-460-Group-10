import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Function;

/**
 * Socket server used by the Digital Twin.
 *
 * It listens for text commands from Multiplexor and passes each command to
 * Crosswalk. Keeping networking here prevents socket code from cluttering the
 * JavaFX drawing code.
 */
public class DigitalTwinServer implements AutoCloseable {

    private final int port;
    private final Function<String, String> commandHandler;
    private ServerSocket serverSocket;
    private volatile boolean running;

    public DigitalTwinServer(int port, Function<String, String> commandHandler) {
        this.port = port;
        this.commandHandler = commandHandler;
    }

    /** Opens the port and starts accepting clients on a background thread. */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
        } catch (IOException e) {
            throw new IllegalStateException("Could not open Digital Twin port " + port, e);
        }

        Thread serverThread = new Thread(this::acceptClients, "digital-twin-server");
        serverThread.setDaemon(true);
        serverThread.start();
        System.out.println("Digital Twin listening on port " + port);
    }

    private void acceptClients() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                Thread clientThread = new Thread(
                        () -> handleClient(client), "digital-twin-client");
                clientThread.setDaemon(true);
                clientThread.start();
            } catch (IOException e) {
                if (running) System.err.println("Socket accept error: " + e.getMessage());
            }
        }
    }

    /** Reads one command per line and sends one response per line. */
    private void handleClient(Socket client) {
        try (Socket socket = client;
             BufferedReader input = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()));
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true)) {

            String command;
            while ((command = input.readLine()) != null) {
                output.println(commandHandler.apply(command));
            }
        } catch (IOException e) {
            System.err.println("Client connection error: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // The application is already closing.
            }
        }
    }
}
