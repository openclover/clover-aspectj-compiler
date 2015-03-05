import clover.com.google.common.collect.Lists;
import clover.org.apache.commons.lang.StringUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class JavaExecutor {

    public static void launchJava(String... params) {
        List<String> args = Lists.newArrayList(
                System.getenv("JAVA_HOME") + File.separator + "bin" + File.separator + "java",
                " ");
        args.addAll(Arrays.asList(params));
        launchCmd(args.toArray(new String[0]));
    }

    public static void launchCmd(String[] cmd) {
        System.out.println("Executing:");
        System.out.println(StringUtils.join(cmd, " "));

        try {
            CommandLine commandLine = CommandLine.parse(StringUtils.join(cmd, " "));
            DefaultExecutor executor = new DefaultExecutor();
            executor.execute(commandLine);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
