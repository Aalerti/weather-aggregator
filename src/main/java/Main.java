import java.util.concurrent.CompletableFuture;

/**
 * Main application class that demonstrates the usage of the WeatherAggregator.
 * It retrieves weather data for London and displays the average temperature and fastest responding source.
 */
public class Main {
    /**
     * The application entry point.
     *
     * @param args Command-line arguments (not used)
     */
    public static void main(String[] args) {

        WeatherAggregator aggregator = new WeatherAggregator();
        CompletableFuture<WeatherReport> report = aggregator.getAggregatedWeather("London");

        report.thenAccept(result -> {
            System.out.println("Средняя температура: " + result.getAverageTemperature());
            System.out.println("Самый быстрый источник: " + result.getFastestSource());
        });

        try {
            report.join();
        } catch (Exception e) {
            System.err.println("Oops...Exception");
        }

    }
}