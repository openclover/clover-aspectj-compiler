package com.atlassian.clover.instr.aspectj;

import com.atlassian.clover.api.instrumentation.InstrumentationSession;
import org.aspectj.ajdt.internal.compiler.ICompilerAdapter;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;


public class CloverAjCompilerAdapter implements ICompilerAdapter {

    public static final char[] RECORDER_FIELD_NAME = "$CLV_R".toCharArray();
    private final ICompilerAdapter originalAdapter;
    private final InstrumentationSession session;
    private final AjInstrumentationConfig config;
    private final LookupEnvironment lookupEnvironment;

    public CloverAjCompilerAdapter(ICompilerAdapter originalAdapter,
                                   InstrumentationSession session,
                                   AjInstrumentationConfig config,
                                   LookupEnvironment lookupEnvironment) {
        this.originalAdapter = originalAdapter;
        this.session = session;
        this.config = config;
        this.lookupEnvironment = lookupEnvironment;
    }

    @Override
    public void afterAnalysing(CompilationUnitDeclaration unit) {
        originalAdapter.afterAnalysing(unit);
    }

    @Override
    public void afterCompiling(CompilationUnitDeclaration[] units) {
        originalAdapter.afterCompiling(units);
    }

    @Override
    public void afterDietParsing(CompilationUnitDeclaration[] units) {
        originalAdapter.afterDietParsing(units);
    }

    @Override
    public void afterGenerating(CompilationUnitDeclaration unit) {
        originalAdapter.afterGenerating(unit);
    }

    @Override
    public void afterProcessing(CompilationUnitDeclaration unit, int unitIndex) {
        originalAdapter.afterProcessing(unit, unitIndex);
    }

    @Override
    public void afterResolving(CompilationUnitDeclaration unit) {
        originalAdapter.afterResolving(unit);
    }

    @Override
    public void beforeAnalysing(CompilationUnitDeclaration unit) {
        originalAdapter.beforeAnalysing(unit);
    }

    @Override
    public void beforeCompiling(ICompilationUnit[] sourceUnits) {
        originalAdapter.beforeCompiling(sourceUnits);
    }

    @Override
    public void beforeGenerating(CompilationUnitDeclaration unit) {
        originalAdapter.beforeGenerating(unit);
    }

    @Override
    public void beforeProcessing(CompilationUnitDeclaration unit) {
        originalAdapter.beforeProcessing(unit);
    }

    @Override
    public void beforeResolving(CompilationUnitDeclaration unit) {
        unit.traverse(
                new CloverAjAstInstrumenter(session, config, originalAdapter, lookupEnvironment),
                unit.scope);
        originalAdapter.beforeResolving(unit);
    }

}
