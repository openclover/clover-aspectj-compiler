package org.aspectj.ajdt.ajc;

import clover.com.google.common.collect.Iterables;
import clover.com.google.common.collect.Lists;
import com.atlassian.clover.CloverInstr;
import com.atlassian.clover.api.CloverException;
import com.atlassian.clover.api.instrumentation.InstrumentationSession;
import com.atlassian.clover.cfg.instr.InstrumentationLevel;
import com.atlassian.clover.instr.aspectj.AjInstrumentationConfig;
import com.atlassian.clover.instr.aspectj.CloverAjBuildManager;
import com.atlassian.clover.registry.Clover2Registry;
import com.atlassian.clover.util.collections.Pair;
import org.aspectj.bridge.IMessageHandler;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 *
 */
@SuppressWarnings("unused") // accessed via reflections
public class CloverAjdtCommand extends AjdtCommand {

    private Clover2Registry registry;
    private InstrumentationSession session;

    @Override
    public boolean runCommand(String[] args, IMessageHandler handler) {
        final Pair<AjInstrumentationConfig, List<String>> configAndArgs = parseConfigurationOptions(args);

        try {
            createCloverRegistry(configAndArgs.first.getInitString());
            startInstrumentation("UTF-8");

            buildManager = new CloverAjBuildManager(handler, session, configAndArgs.first);
            savedArgs = Iterables.toArray(configAndArgs.second, String.class); // pass filtered arguments for AJC
            boolean ret = doCommand(handler, false);

            endInstrumentation(true);
            return ret;
        } catch (CloverException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Parse Clover-related settings. It handles a subset of CloverInstr options. We shall avoid any conflicts
     * with original AJC options https://eclipse.org/aspectj/doc/next/devguide/ajc-ref.html
     *
     * @param args
     * @return Pair of: AjInstrumentationConfig (Clover settings) and List&lt;String&gt; (arguments for AJC)
     * @see CloverInstr#processArgs(java.lang.String[])
     */
    protected Pair<AjInstrumentationConfig, List<String>> parseConfigurationOptions(String[] args) {
        final AjInstrumentationConfig config = new AjInstrumentationConfig();
        final List<String> ajcArgs = Lists.newArrayList();

        // See also: com.atlassian.clover.CloverInstr#processArgs(String[])
        int i = 0;
        while (i < args.length) {
            if (args[i].equals("--initstring")) {
                i++;
                config.setInitstring(args[i]);
            } else if (args[i].equals("--instrlevel")) {
                i++;
                String instr = args[i].toUpperCase(Locale.US);
                config.setInstrLevel(InstrumentationLevel.valueOf(instr));
            } else if (args[i].equals("--instrAST")) {
                i++;
                boolean instrAST = Boolean.valueOf(args[i]);
                config.setInstrumentAST(instrAST);
            } else if (args[i].equals("--encoding")) {
                i++;
                config.setEncoding(args[i]);
            } else {
                // not Clover one? pass it to AJC
                ajcArgs.add(args[i]);
            }
            i++;
        }

        return Pair.of(config, ajcArgs);
    }

    protected void createCloverRegistry(String initString) throws CloverException {
        // create clover database
        try {
            final File dbFile = new File(initString);
            registry = Clover2Registry.createOrLoad(dbFile, "ajc-example");
            if (registry == null) {
                throw new CloverException("Unable to create or load clover registry located at: " + dbFile);
            }
        } catch (IOException e) {
            throw new CloverException(e);
        }
    }

    protected void startInstrumentation(String encoding) throws CloverException {
        session = registry.startInstr(encoding);
    }

    protected Clover2Registry endInstrumentation(boolean append) throws CloverException {
        try {
            session.close();
            if (append) {
                registry.saveAndAppendToFile();
            } else {
                registry.saveAndOverwriteFile();
            }
            return registry;
        } catch (IOException e) {
            throw new CloverException(e);
        }
    }

}
