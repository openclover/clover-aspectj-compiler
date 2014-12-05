package org.aspectj.ajdt.ajc;

import com.atlassian.clover.api.CloverException;
import com.atlassian.clover.api.instrumentation.InstrumentationSession;
import com.atlassian.clover.instr.aspectj.CloverAjBuildManager;
import com.atlassian.clover.registry.Clover2Registry;
import org.aspectj.bridge.IMessageHandler;

import java.io.File;
import java.io.IOException;

/**
 *
 */
@SuppressWarnings("unused") // accessed via reflections
public class CloverAjdtCommand extends AjdtCommand {

    private Clover2Registry registry;
    private InstrumentationSession session;

    @Override
    public boolean runCommand(String[] args, IMessageHandler handler) {
        // TODO fetch initstring from args
        final String initString = ".clover/clover.db";

        try {
            createCloverRegistry(initString);
            startInstrumentation("UTF-8");

            buildManager = new CloverAjBuildManager(handler, session, initString);
            savedArgs = new String[args.length];
            System.arraycopy(args, 0, savedArgs, 0, savedArgs.length);
            boolean ret = doCommand(handler, false);

            endInstrumentation(true);
            return ret;
        } catch (CloverException e) {
            e.printStackTrace();
        }
        return false;
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
