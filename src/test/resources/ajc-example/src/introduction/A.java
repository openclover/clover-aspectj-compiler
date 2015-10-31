package introduction;

import org.jetbrains.annotations.Nullable;

@interface Xyz {
    String something();
    Class[] all();
}

public final class A {
    static int PI = 314;
//    public static final com_atlassian_clover.CoverageRecorder $CLV_Z = null; /*com_atlassian_clover.Clover.getRecorder(
//        ".clover/clover2.db", 123L, 456L, 789, null, null);*/

    static {
        // we need this due to a bug
    }

    @Xyz(something = "foo", all = { String.class, Object.class})
    void foo() {
        int i = 0;

        $CLV_R.inc(0);
        $CLV_R.inc(1);
        $CLV_R.inc(2);
        $CLV_R.flush();
    }

    protected <T, S extends Object> T goo(S input) {
        return null;
    }

    public static void main(String[] args) {
        System.out.println("$CLV_R=" + $CLV_R);
//        System.out.println("$CLV_Z=" + $CLV_Z);

        new A().foo();
        System.out.println("$CLV_R.iget(2)=" + $CLV_R.iget(2));
        $CLV_R.flush();
    }

}