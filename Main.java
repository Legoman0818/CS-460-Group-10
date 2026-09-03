import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javafx.application.Application;

/**
 * Assignment test harness.
 *
 * Run this file. It launches the Digital Twin GUI, waits for its socket server,
 * and waits for Enter before exercising every device through Multiplexor.
 */
public class Main {

    private static final String[] SIGNALS = {
        "NORTH_LEFT", "NORTH_STRAIGHT", "NORTH_RIGHT",
        "SOUTH_LEFT", "SOUTH_STRAIGHT", "SOUTH_RIGHT",
        "EAST_LEFT", "EAST_STRAIGHT", "EAST_RIGHT",
        "WEST_LEFT", "WEST_STRAIGHT", "WEST_RIGHT"
    };

    private static final String[] PEDESTRIANS = {"NW", "NE", "SW", "SE"};

    public static void main(String[] args) {
        /*
         * Application.launch blocks until the window closes, so the test
         * harness runs on a separate background thread.
         */
        Thread tests = new Thread(Main::runTests, "main-test-harness");
        tests.setDaemon(true);
        tests.start();

        Application.launch(Crosswalk.class, args);
    }

    private static void runTests() {
        try {
            // Give JavaFX enough time to draw the window and open port 5000.
            Thread.sleep(1500);

            try (Multiplexor mux = connectToDigitalTwin()) {
                // The GUI stays in its normal state until the presenter is ready.
                System.out.println("Press Enter to run the Digital Twin tests...");
                BufferedReader console = new BufferedReader(
                        new InputStreamReader(System.in));
                console.readLine();

                verify(mux.ping());
                System.out.println("Connection test passed");

                // Exercise all 12 programmable traffic signals.
                for (String signal : SIGNALS) {
                    verify(mux.setSignal(signal, "GREEN"));
                    Thread.sleep(500);
                    verify(mux.setSignal(signal, "YELLOW"));
                    Thread.sleep(250);
                    verify(mux.setSignal(signal, "RED"));
                }
                System.out.println("Traffic-signal tests passed");

                // Exercise all four pedestrian interfaces.
                for (String zone : PEDESTRIANS) {
                    verify(mux.setPedestrian(zone, true));
                    Thread.sleep(500);
                    verify(mux.setPedestrian(zone, false));
                }
                System.out.println("Pedestrian-interface tests passed");

                // Exercise the antenna/emergency interface last.
                verify(mux.activateEmergencyMode());
                System.out.println("Antenna test passed");
                System.out.println("All Digital Twin device tests completed.");
            }
        } catch (IOException e) {
            System.err.println("Test harness error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Retry briefly in case the JavaFX window starts slowly on another PC. */
    private static Multiplexor connectToDigitalTwin()
            throws IOException, InterruptedException {
        IOException lastError = null;

        for (int attempt = 1; attempt <= 10; attempt++) {
            try {
                return new Multiplexor("localhost", 5000);
            } catch (IOException e) {
                lastError = e;
                Thread.sleep(300);
            }
        }

        throw lastError;
    }

    /** Stop the test immediately if the Digital Twin rejects any command. */
    private static void verify(String response) throws IOException {
        if (response == null || !response.startsWith("OK")) {
            throw new IOException("Command failed: " + response);
        }
    }
}
