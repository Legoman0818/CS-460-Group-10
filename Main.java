import javafx.application.Application;

/**
 * Entry point for the Traffic Control System program.
 *
 * This class intentionally stays small. JavaFX creates the window and runs
 * its event loop inside Crosswalk, so Main only tells JavaFX which Application
 * class should be launched.
 */
public class Main {

    /** Starts JavaFX and forwards any command-line arguments to Crosswalk. */
    public static void main(String[] args) {
        Application.launch(Crosswalk.class, args);
    }
}
