import javafx.application.Application;

/**
 * Main - starts the whole program.
 */
public class Main {

    public static void main(String[] args) {

        Thread twin = new Thread(
                () -> Application.launch(Crosswalk.class, args), "digital-twin");
        twin.start();

        Controller controller = new Controller("localhost", Crosswalk.PORT);
        controller.run();
    }
}
