import config.DatabaseConfig;
import generator.EventGenerator;
import model.Event;
import repository.EventRepository;

public class Main {

    private static final int EVENT_COUNT = 1000;

    public static void main(String[] args) {
        EventGenerator generator = new EventGenerator();
        EventRepository repository = new EventRepository();

        System.out.println("Starting event generation: " + EVENT_COUNT + " events");

        int success = 0;
        int failure = 0;

        for (int i = 0; i < EVENT_COUNT; i++) {
            try {
                Event event = generator.generate();
                repository.save(event);
                success++;

                if (success % 100 == 0) {
                    System.out.println("Saved " + success + " events...");
                }
            } catch (Exception e) {
                failure++;
                System.err.println("Failed to save event: " + e.getMessage());
            }
        }

        System.out.println("Done. success=" + success + ", failure=" + failure);
        DatabaseConfig.close();
    }
}

