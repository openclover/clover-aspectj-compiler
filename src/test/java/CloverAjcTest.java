import com.atlassian.clover.CloverStartup;
import com.atlassian.clover.Logger;
import com.atlassian.clover.instr.aspectj.CloverAjc;
import com.atlassian.clover.reporters.html.HtmlReporter;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * High-level test for {@link com.atlassian.clover.instr.aspectj.CloverAjc}
 */
public class CloverAjcTest {

    public static final String AJC_EXAMPLE_DIR = "src/test/resources/ajc-example";
    public static final String CLOVER_DB_PATH = AJC_EXAMPLE_DIR + "/clover/clover.db";
    public static final String CLOVER_REPORT_PATH = AJC_EXAMPLE_DIR + "/clover";

    @Test
    public void testAjcExampleWithClover() {
        // build aspectj project with clover
        String[] compilerArgs = {
                // Clover stuff
                // TODO "-i", CLOVER_DB_PATH,
                // AJC stuff
                "-sourceroots", AJC_EXAMPLE_DIR,
                "-noExit"
        };
        CloverAjc.main(compilerArgs);

        assertTrue(new File(AJC_EXAMPLE_DIR, "CloneablePoint.class").exists());
        assertTrue(new File(CLOVER_DB_PATH).exists());

        // generate html report
        CloverStartup.loadLicense(Logger.getInstance());
        String [] reporterArgs = {
                "-i", CLOVER_DB_PATH,
                "-o", CLOVER_REPORT_PATH
        };
        int result = HtmlReporter.runReport(reporterArgs);
        assertEquals(0, result);
        assertTrue(new File(CLOVER_REPORT_PATH, "dashboard.html").exists());
    }

}
