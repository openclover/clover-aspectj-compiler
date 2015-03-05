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
    public static final String CLOVER_DB_PATH = ".clover/clover.db"; // AJC_EXAMPLE_DIR + "/clover/clover.db";
    public static final String CLOVER_REPORT_PATH = AJC_EXAMPLE_DIR + "/target/clover";
    public static final String SOURCE_DIR = AJC_EXAMPLE_DIR + "/src";
    public static final String TARGET_CLASSES_DIR = AJC_EXAMPLE_DIR + "/target/classes";

    @Test
    public void testAjcExampleWithClover() {
        // build aspectj project with clover
        String[] compilerArgs = {
                // Clover stuff
                // TODO "-i", CLOVER_DB_PATH,
                // AJC stuff
                "-sourceroots", SOURCE_DIR,
                "-d", TARGET_CLASSES_DIR,
                "-noExit"
        };
        CloverAjc.main(compilerArgs);

        assertTrue(new File(TARGET_CLASSES_DIR, "introduction/CloneablePoint.class").exists());
        assertTrue(new File(CLOVER_DB_PATH).exists());

        // run classes
        String M2 = "C:\\Users\\Marek\\.m2\\repository\\";
        String CLASSPATH = M2 + "org\\aspectj\\aspectjrt\\1.8.4\\aspectjrt-1.8.4.jar;"
                + M2 + "com\\atlassian\\clover\\clover\\4.0.2\\clover-4.0.2.jar;"
                + TARGET_CLASSES_DIR;
        JavaExecutor.launchJava("-cp", CLASSPATH, "introduction.CloneablePoint");
        JavaExecutor.launchJava("-cp", CLASSPATH, "introduction.ComparablePoint");
        JavaExecutor.launchJava("-cp", CLASSPATH, "introduction.HashablePoint");
        JavaExecutor.launchJava("-cp", CLASSPATH, "introduction.Point");

        // generate html report
        CloverStartup.loadLicense(Logger.getInstance());
        String [] reporterArgs = {
                "-i", CLOVER_DB_PATH,
                "-o", CLOVER_REPORT_PATH,
                "-a",
                "-e",
                "-d"
        };
        int result = HtmlReporter.runReport(reporterArgs);
        assertEquals(0, result);
        assertTrue(new File(CLOVER_REPORT_PATH, "dashboard.html").exists());
    }

}
