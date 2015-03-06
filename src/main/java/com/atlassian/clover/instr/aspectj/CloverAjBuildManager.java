package com.atlassian.clover.instr.aspectj;

import com.atlassian.clover.api.instrumentation.InstrumentationSession;
import org.aspectj.ajdt.internal.compiler.ICompilerAdapter;
import org.aspectj.ajdt.internal.core.builder.AjBuildManager;
import org.aspectj.bridge.IMessageHandler;

/**
 *
 */
public class CloverAjBuildManager extends AjBuildManager {

    private final InstrumentationSession session;
    private final String initString;

    public CloverAjBuildManager(IMessageHandler holder, InstrumentationSession session, String initString) {
        super(holder);
        this.session = session;
        this.initString = initString;
    }

    @Override
    public ICompilerAdapter getAdapter(org.aspectj.org.eclipse.jdt.internal.compiler.Compiler forCompiler) {
        return new CloverAjCompilerAdapter(super.getAdapter(forCompiler), session, initString, forCompiler.lookupEnvironment);
    }

}
