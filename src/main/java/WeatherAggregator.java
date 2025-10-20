import java.util.concurrent.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * A service that aggregates weather information from multiple API sources.
 * This class simulates calls to different weather APIs, handles failures,
 * and provides a combined weather report with statistics.
 */
public class WeatherAggregator {

    /**
     * Retrieves the temperature for a city from the first API.
     *
     * @param city The city for which to retrieve the temperature
     * @return A CompletableFuture containing the temperature value or null if the API fails
     */
    private CompletableFuture<Integer> getTemperatureFromAPI1(String city) {
        return getTemperatureFromAPI("API1", 24);
    }

    /**
     * Retrieves the temperature for a city from the second API.
     *
     * @param city The city for which to retrieve the temperature
     * @return A CompletableFuture containing the temperature value or null if the API fails
     */
    private CompletableFuture<Integer> getTemperatureFromAPI2(String city) {
        return getTemperatureFromAPI("API2", 35);
    }

    /**
     * Retrieves the temperature for a city from the third API.
     *
     * @param city The city for which to retrieve the temperature
     * @return A CompletableFuture containing the temperature value or null if the API fails
     */
    private CompletableFuture<Integer> getTemperatureFromAPI3(String city) {
        return getTemperatureFromAPI("API3", 54);
    }

    /**
     * Simulates an API call to retrieve temperature.
     * The method introduces a random delay (1-3 seconds) and has a 50% chance of failure.
     *
     * @param apiName The name of the API source
     * @param temperature The temperature value to return on success
     * @return A CompletableFuture containing the temperature value or null if the API fails
     */
    private CompletableFuture<Integer> getTemperatureFromAPI(String apiName, int temperature) {
        Random random = new Random();
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(random.nextInt(1000, 3001));
                if (Math.random() > 0.5) throw new RuntimeException("Fail in " + apiName);
                return temperature;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }).handle( (res, ex) -> {
            if (ex != null) {
                System.err.println("Error: " + ex.getMessage());
                return null;
            }
            return res;
        });
    }

    /**
     * Aggregates weather data from multiple API sources for the specified city.
     * This method queries three different weather APIs in parallel and combines the results.
     * It tracks which API responds fastest and calculates the average temperature.
     * A timeout of 5 seconds is applied to the entire operation.
     *
     * @param city The city for which to retrieve weather information
     * @return A CompletableFuture containing a WeatherReport with aggregated data
     */
    public CompletableFuture<WeatherReport> getAggregatedWeather(String city) {

        CompletableFuture<Integer> api1 = getTemperatureFromAPI1(city);
        CompletableFuture<Integer> api2 = getTemperatureFromAPI2(city);
        CompletableFuture<Integer> api3 = getTemperatureFromAPI3(city);

        CompletableFuture<String> fastest = getFastestAPI(api1, api2, api3);

        return CompletableFuture.allOf(api1, api2, api3)
                .orTimeout(5, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    System.err.println("Timeout: " + ex.getMessage());
                    return null;
                })
                .thenApply(v -> {
                    Integer temp1 = api1.join();
                    Integer temp2 = api2.join();
                    Integer temp3 = api3.join();
                    String fastestName = fastest.getNow("Unknown");

                    Map<String, Integer> sourceTemperatures = new HashMap<>();
                    List<String> failedSources = new ArrayList<>();

                    if (temp1 != null) sourceTemperatures.put("API1", temp1);
                    else failedSources.add("API1");

                    if (temp2 != null) sourceTemperatures.put("API2", temp2);
                    else failedSources.add("API2");

                    if (temp3 != null) sourceTemperatures.put("API3", temp3);
                    else failedSources.add("API3");

                    double average = sourceTemperatures.values().stream()
                            .mapToInt(temperature -> temperature)
                            .average()
                            .orElse(0.0);

                    return new WeatherReport(average, sourceTemperatures, failedSources, fastestName);
                });
    }

    /**
     * Determines which of the three API calls completes successfully first.
     *
     * @param api1 CompletableFuture for the first API call
     * @param api2 CompletableFuture for the second API call
     * @param api3 CompletableFuture for the third API call
     * @return A CompletableFuture containing the name of the fastest responding API
     */
    private CompletableFuture<String> getFastestAPI(CompletableFuture<Integer> api1,
                                                    CompletableFuture<Integer> api2,
                                                    CompletableFuture<Integer> api3) {
        // Create a future for the result
        CompletableFuture<String> result = new CompletableFuture<>();

        // Subscribe to the completion of each API
        api1.whenComplete((value, ex) -> {
            if (ex == null && value != null) { // Успешное завершение
                result.complete("API1");
            }
        });

        api2.whenComplete((value, ex) -> {
            if (ex == null && value != null) {
                result.complete("API2");
            }
        });

        api3.whenComplete((value, ex) -> {
            if (ex == null && value != null) {
                result.complete("API3");
            }
        });

        // If all APIs fail, set to "Unknown"
        CompletableFuture.allOf(api1, api2, api3)
                .exceptionally(ex -> null)
                .thenRun(() -> {
                    if (!result.isDone()) {
                        result.complete("Unknown");
                    }
                });

        return result;
    }

    /**
     * Implements a retry mechanism for asynchronous operations that may fail.
     * Retries a failed operation with increasing delays between attempts.
     *
     * @param <T> The type of result returned by the operation
     * @param supplier A supplier function that produces a CompletableFuture to retry
     * @param maxRetries The maximum number of retry attempts
     * @return A CompletableFuture that will be completed with the result or exception after retries
     */
    public <T> CompletableFuture<T> retry(Supplier<CompletableFuture<T>> supplier,
                                          int maxRetries) {
        CompletableFuture<T> future = new CompletableFuture<>();
        retryInternal(supplier, maxRetries, maxRetries, future);
        return future;
    }

    /**
     * Internal recursive method to implement the retry logic.
     * Uses exponential backoff with a maximum delay of 5 seconds.
     *
     * @param <T> The type of result returned by the operation
     * @param supplier A supplier function that produces a CompletableFuture
     * @param retriesLeft Number of retry attempts remaining
     * @param totalRetries Total number of retry attempts allowed
     * @param result The CompletableFuture to complete with the final result
     */
    private <T> void retryInternal(Supplier<CompletableFuture<T>> supplier,
                                   int retriesLeft,
                                   int totalRetries,
                                   CompletableFuture<T> result) {
        supplier.get().whenComplete((value, exception) -> {
            if (exception == null) {
                result.complete(value);
            }
            else if (retriesLeft > 0) {
                int attempt = totalRetries - retriesLeft + 1;
                CompletableFuture.delayedExecutor(Math.min(attempt * 1000, 5000), TimeUnit.MILLISECONDS)
                        .execute(() -> {
                            retryInternal(supplier, retriesLeft-1, totalRetries, result);
                        });
            }
            else {
                result.completeExceptionally(exception);
            }
        });
    }
}

/**
 * A data class that holds aggregated weather information from multiple sources.
 * It contains temperature data, statistics about which sources succeeded or failed,
 * and which source responded fastest.
 */
class WeatherReport {
    private double averageTemperature;
    private Map<String, Integer> sourceTemperatures;
    private List<String> failedSources;
    private String fastestSource;

    /**
     * Creates a new WeatherReport with the specified data.
     *
     * @param averageTemperature The average temperature across all successful sources
     * @param sourceTemperatures A map of source names to their reported temperatures
     * @param failedSources A list of sources that failed to provide data
     * @param fastestSource The name of the source that responded fastest
     */
    public WeatherReport(double averageTemperature, Map<String, Integer> sourceTemperatures, List<String> failedSources, String fastestSource) {
        this.sourceTemperatures = sourceTemperatures;
        this.averageTemperature = averageTemperature;
        this.failedSources = failedSources;
        this.fastestSource = fastestSource;
    }

    /**
     * Creates an empty WeatherReport.
     */
    public WeatherReport() {
    }

    /**
     * Gets the average temperature from all successful API sources.
     *
     * @return The average temperature across all successful sources
     */
    public double getAverageTemperature() {
        return averageTemperature;
    }

    /**
     * Gets the map of temperatures from individual sources.
     *
     * @return A map of source names to their reported temperatures
     */
    public Map<String, Integer> getSourceTemperatures() {
        return sourceTemperatures;
    }

    /**
     * Gets the list of sources that failed to return data.
     *
     * @return A list of sources that failed to provide data
     */
    public List<String> getFailedSources() {
        return failedSources;
    }

    /**
     * Gets the name of the source that responded fastest.
     *
     * @return The name of the source that responded fastest
     */
    public String getFastestSource() {
        return fastestSource;
    }

    /**
     * Returns a string representation of the WeatherReport.
     *
     * @return A string representation of the WeatherReport
     */
    @Override
    public String toString() {
        return "WeatherReport{" +
                "averageTemperature=" + averageTemperature +
                ", sourceTemperatures=" + sourceTemperatures +
                ", failedSources=" + failedSources +
                ", fastestSource='" + fastestSource + '\'' +
                '}';
    }
}