package com.atlassian.clover.instr.aspectj;

import com.atlassian.clover.cfg.instr.InstrumentationConfig;
import com.atlassian.clover.cfg.instr.InstrumentationLevel;

import java.io.Serializable;

public class AjInstrumentationConfig implements Serializable {

    private final InstrumentationConfig baseConfig;

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

}
