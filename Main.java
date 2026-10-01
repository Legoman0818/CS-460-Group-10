import javafx.application.Application;

/**
 * Main - starts the whole program. (Walkthrough 3.1)
 *
 * Design Diagram: not on the diagram, this just starts everything up.
 *
 * It does two things:
 *   1. opens the twin window (Crosswalk) on its own thread
 *   2. makes the Controller and calls run() on the main thread
 */
public class Main {

    public static void main(String[] args) {
        // launch() doesn't come back until the window is closed, so the window
        // needs its own thread or the Controller would never get to start
        Thread twin = new Thread(
                () -> Application.launch(Crosswalk.class, args), "digital-twin");
        twin.start();

        // no waiting needed here, the Multiplexor keeps trying to connect
        // until the window's server is open (see Multiplexor.connect)
        Controller controller = new Controller("localhost", Crosswalk.PORT);
        controller.run();
    }
}
