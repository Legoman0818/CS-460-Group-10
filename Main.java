import javafx.application.Application;

/**
 * Starts the program. First the digital twin window (Crosswalk) is opened,
 * then the Controller is created and run() is called.
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        // launch() doesn't return until the window closes, so the window gets
        // its own thread and the controller runs on the main thread.
        Thread twin = new Thread(
                () -> Application.launch(Crosswalk.class, args), "digital-twin");
        twin.start();

        // wait for the twin's server to be ready before connecting to it
        Crosswalk.awaitReady();

        Controller controller = new Controller("localhost", Crosswalk.PORT);
        controller.run();
    }
}
