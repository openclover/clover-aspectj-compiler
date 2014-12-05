package com.atlassian.clover.instr.aspectj;

import org.aspectj.ajdt.ajc.CloverAjdtCommand;

/**
 * AspectJ compiler with Clover code coverage
 */
public class CloverAjc extends org.aspectj.tools.ajc.Main {

    public CloverAjc() {
        super();
        // override compiler command - use our version
        super.commandName = CloverAjdtCommand.class.getName();
    }

    public static void main(String[] args) {
        final CloverAjc ajc = new CloverAjc();
        ajc.runMain(args, true);
    }

}
