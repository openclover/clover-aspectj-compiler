package com.atlassian.clover.instr.aspectj;

import com.atlassian.clover.cfg.instr.InstrumentationConfig;
import com.atlassian.clover.cfg.instr.InstrumentationLevel;

import java.io.Serializable;

public class AjInstrumentationConfig implements Serializable {

    private final InstrumentationConfig baseConfig;
    private boolean instrumentAST = true;
    private String encoding;

    public AjInstrumentationConfig() {
        baseConfig = new InstrumentationConfig();
        baseConfig.setInitstring(".clover/clover.db");
        baseConfig.setInstrLevel(InstrumentationLevel.STATEMENT.ordinal());
    }

    public String getInitString() {
        return baseConfig.getInitString();
    }

    public void setInitstring(String initstring) {
        baseConfig.setInitstring(initstring);
    }

    public InstrumentationLevel getInstrLevel() {
        return InstrumentationLevel.values()[baseConfig.getInstrLevel()];
    }

    public void setInstrLevel(InstrumentationLevel instrLevel) {
        baseConfig.setInstrLevel(instrLevel.ordinal());
    }

    /**
     * Wheter to add Clover instrumentation to the AST. If set to false, Clover will still record methods and
     * statements in the database, but will not add any recorder calls or fields to the AST. For debugging purposes only.
     */
    public boolean isInstrumentAST() {
        return instrumentAST;
    }

    public void setInstrumentAST(boolean instrumentAST) {
        this.instrumentAST = instrumentAST;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
}
