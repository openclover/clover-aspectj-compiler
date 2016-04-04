import clover.org.apache.commons.lang.StringUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;

import java.io.File;
import java.io.IOException;

public class JavaExecutor {

    public static void launchJava(final String... params) {
        final String javaExecutable = System.getenv("JAVA_HOME") + File.separator + "bin" + File.separator + "java";
        launchCommand(javaExecutable, params);
    }

    private static void launchCommand(final String executable, final String... arguments) {
        System.out.println("Executing:");
        System.out.println(executable + " " + StringUtils.join(arguments, " "));

        try {
            final CommandLine commandLine = new CommandLine(executable);
            commandLine.addArguments(arguments);
            final DefaultExecutor executor = new DefaultExecutor();
            executor.execute(commandLine);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
