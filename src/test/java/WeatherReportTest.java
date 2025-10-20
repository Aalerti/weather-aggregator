import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the WeatherReport class.
 */
public class WeatherReportTest {

    private WeatherReport report;
    private Map<String, Integer> sourceTemperatures;
    private List<String> failedSources;

    @BeforeEach
    public void setUp() {
        sourceTemperatures = new HashMap<>();
        sourceTemperatures.put("API1", 20);
        sourceTemperatures.put("API2", 25);

        failedSources = new ArrayList<>();
        failedSources.add("API3");

        report = new WeatherReport(22.5, sourceTemperatures, failedSources, "API1");
    }

    @Test
    public void testConstructorAndGetters() {
        assertEquals(22.5, report.getAverageTemperature());
        assertEquals(sourceTemperatures, report.getSourceTemperatures());
        assertEquals(failedSources, report.getFailedSources());
        assertEquals("API1", report.getFastestSource());
    }

    @Test
    public void testEmptyConstructor() {
        WeatherReport emptyReport = new WeatherReport();
        assertNull(emptyReport.getSourceTemperatures());
        assertNull(emptyReport.getFailedSources());
        assertNull(emptyReport.getFastestSource());
        assertEquals(0.0, emptyReport.getAverageTemperature());
    }

    @Test
    public void testToString() {
        String reportString = report.toString();

        assertTrue(reportString.contains("averageTemperature=22.5"));
        assertTrue(reportString.contains("sourceTemperatures={API2=25, API1=20}"));
        assertTrue(reportString.contains("failedSources=[API3]"));
        assertTrue(reportString.contains("fastestSource='API1'"));
    }

    @Test
    public void testMapModification() {
        // Test that the returned map is the actual map, not a copy
        Map<String, Integer> temps = report.getSourceTemperatures();
        temps.put("API4", 30);

        assertEquals(3, report.getSourceTemperatures().size());
        assertEquals(Integer.valueOf(30), report.getSourceTemperatures().get("API4"));
    }

    @Test
    public void testListModification() {
        // Test that the returned list is the actual list, not a copy
        List<String> failed = report.getFailedSources();
        failed.add("API4");

        assertEquals(2, report.getFailedSources().size());
        assertTrue(report.getFailedSources().contains("API4"));
    }
}