package instrumentation;

public class StatementInstrumentation {

    static final int static_i;
    final int instance_i;

    static {
        static_i = 10;
    }

    {
        instance_i = 20;
    }

    public StatementInstrumentation() {
        int i = 0;
        i++;
    }

    public void simpleStatements() {
        int i = 0;
        i++;
    }

    public void forEachLoop() {
        int i = 0;
        for (int j = 0;
            j < 10;
            j++) {
            i++;
        }
        i++;
    }

    public void doWhileLoop() {
        int j = 0;
        do {
            j++;
        } while (j < 10);
        j++;
    }

    public void whileLoop() {
        int j = 0;
        while (j < 10) {
            j++;
        }
    }

    public void tryCatchFinally() {
        int j = 0;
        try {
            j++;
        } catch (RuntimeException e) {
            j++;
        } catch (Exception e) {
            j++;
        } finally {
            j++;
        }
        j++;
    }

    public void ifThenElse() {
        int j = 0;
        if (j < 10) {
            j++;
        }

        if (j < 10) {
            j++
        } else {
            j--;
        }

        if (j < 10) {

        } else {

        }
    }

    public void switchCaseDefault() {
        int j = 1, k;
        switch (j) {
            case 0:
                k = 0;
                break;
            case 1:
                k = 10;
                // fall-through
            case 2:
                k = 20;
                break;
            default:
                k = 30;
        }
    }

    public static void main(String[] args) {
        StatementInstrumentation s = new StatementInstrumentation();
        s.simpleStatements();
        s.forEachLoop();
        s.doWhileLoop();
        s.whileLoop();
        s.tryCatchFinally();
        s.ifThenElse();
        s.switchCaseDefault();
    }
}