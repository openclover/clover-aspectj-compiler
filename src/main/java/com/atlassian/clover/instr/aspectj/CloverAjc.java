package com.atlassian.clover.instr.aspectj;

import org.aspectj.ajdt.ajc.CloverAjdtCommand;
import org.aspectj.bridge.IMessage;
import org.aspectj.bridge.IMessageHolder;
import org.aspectj.bridge.MessageHandler;
import org.aspectj.util.LangUtil;

import java.util.List;

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

    public static int bareMain(String[] args, boolean useSystemExit, List fails, List errors, List warnings, List infos) {
        final CloverAjc ajc = new CloverAjc();
        MessageHandler holder = new MessageHandler();
        ajc.setHolder(holder);
        try {
            ajc.runMain(args, useSystemExit);
        } finally {
            readMessages(holder, IMessage.FAIL, true, fails);
            readMessages(holder, IMessage.ERROR, false, errors);
            readMessages(holder, IMessage.WARNING, false, warnings);
            readMessages(holder, IMessage.INFO, false, infos);
        }
        return holder.numMessages(IMessage.ERROR, true);
    }

    private static void readMessages(IMessageHolder holder, IMessage.Kind kind, boolean orGreater, List<String> sink) {
        if ((null == sink) || (null == holder)) {
            return;
        }
        IMessage[] messages = holder.getMessages(kind, orGreater);
        if (!LangUtil.isEmpty(messages)) {
            for (IMessage message : messages) {
                sink.add(MessagePrinter.render(message));
            }
        }
    }

}
