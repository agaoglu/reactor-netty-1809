package app;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Autowired
    private ObjectProvider<Collector> collectorProvider;

    @Override
    public void run(String... args) throws Exception {
        String[] addresses = new String[]{
                // Addresses accepting connections but hanging up at first request leaks metrics
                "rtsp://google.com:80",
                // Retrying unreachable addresses causes no problems
                "rtsp://192.168.1.150:554"
        };

        for (String address : addresses) {
            Collector collector = collectorProvider.getObject(address);
            collector.start();
        }
    }
}
