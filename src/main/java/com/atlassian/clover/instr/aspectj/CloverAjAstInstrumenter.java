package com.atlassian.clover.instr.aspectj;

import com.atlassian.clover.api.instrumentation.InstrumentationSession;
import com.atlassian.clover.context.ContextSet;
import com.atlassian.clover.registry.FixedSourceRegion;
import com.atlassian.clover.registry.entities.MethodSignature;
import com.atlassian.clover.registry.entities.Modifier;
import com.atlassian.clover.registry.entities.Modifiers;
import com.atlassian.clover.registry.entities.Parameter;
import com.atlassian.clover.spi.lang.LanguageConstruct;
import com.atlassian.clover.util.collections.Pair;
import org.aspectj.org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.LabeledStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.TryStatement;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.ClassScope;

/**
 *
 */
public class CloverAjAstInstrumenter extends ASTVisitor {

    private final InstrumentationSession session;

    public CloverAjAstInstrumenter(InstrumentationSession session) {
        this.session = session;
    }

    @Override
    public void endVisit(LabeledStatement node, BlockScope scope) {
        super.visit(node, scope);
//        node.getLocationInParent();
    }

    @Override
    public void endVisit(TryStatement tryStatement, BlockScope scope) {
        super.endVisit(tryStatement, scope);
    }

    // instrument methods

    @Override
    public boolean visit(MethodDeclaration methodDeclaration, ClassScope scope) {
        Pair<Integer, Integer> lineCol = charIndexToLineCol(methodDeclaration.declarationSourceStart);
        session.enterMethod(new ContextSet(),
                new FixedSourceRegion(lineCol.first, lineCol.second),
                extractMethodSignature(methodDeclaration),
                false, null, false, 0,
                LanguageConstruct.Builtin.METHOD);
        return super.visit(methodDeclaration, scope);

    }


    @Override
    public void endVisit(MethodDeclaration methodDeclaration, ClassScope scope) {
        Pair<Integer, Integer> lineCol = charIndexToLineCol(methodDeclaration.declarationSourceEnd);
        session.exitMethod(lineCol.first, lineCol.second);
        super.endVisit(methodDeclaration, scope);
    }

    protected MethodSignature extractMethodSignature(MethodDeclaration methodDeclaration) {
        // TODO create a real method signature
        return new MethodSignature("foo", null, "void", new Parameter[0], new String[0],
                Modifiers.createFrom(Modifier.PUBLIC, null));
    }

    protected Pair<Integer, Integer> charIndexToLineCol(int charIndex) {
        // TODO convert char index to line:column
        return Pair.of(Integer.valueOf(10), Integer.valueOf(80));
    }
}
