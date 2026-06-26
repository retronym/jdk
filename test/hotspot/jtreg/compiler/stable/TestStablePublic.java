/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test TestStablePublic
 * @summary tests that the public java.lang.annotation.Stable is honored for
 *          fields of classes loaded by an application (non-boot) class loader.
 *          Note: this test deliberately does NOT use /bootclasspath, so the
 *          test classes are defined by the application class loader.  The
 *          internal jdk.internal.vm.annotation.Stable would be ignored here;
 *          the public annotation must be honored.
 *
 * @run main/othervm -Xcomp
 *                   -XX:CompileOnly=*TestStablePublic*::get*
 *                   -XX:-TieredCompilation
 *                   -XX:+FoldStableValues
 *                   compiler.stable.TestStablePublic true
 *
 * @run main/othervm -Xcomp
 *                   -XX:CompileOnly=*TestStablePublic*::get*
 *                   -XX:-TieredCompilation
 *                   -XX:-FoldStableValues
 *                   compiler.stable.TestStablePublic false
 */

package compiler.stable;

import java.lang.annotation.Stable;

public class TestStablePublic {

    static boolean isStableEnabled;

    public static void main(String[] args) throws Exception {
        isStableEnabled = Boolean.parseBoolean(args[0]);

        // Guard: the test classes must not be boot-loaded, otherwise this
        // would not distinguish the public annotation from the internal one.
        ClassLoader cl = TestStablePublic.class.getClassLoader();
        if (cl == null) {
            throw new Error("Test classes are boot-loaded; the test cannot "
                          + "distinguish the public @Stable from the internal one.");
        }
        System.out.println("@Stable:         " + (isStableEnabled ? "enabled" : "disabled"));
        System.out.println("class loader:    " + cl);

        run(ObjectStable.class);
        run(IntStable.class);
        run(ObjectArrayDim1.class);

        if (failed) {
            throw new Error("TEST FAILED");
        }
    }

    /* ==================================================== */

    enum Values {A, B, C}

    static class ObjectStable {
        public @Stable Values v;

        public static final ObjectStable c = new ObjectStable();
        public static Values get() { return c.v; }
        public static void test() throws Exception {
            c.v = Values.A; Values val1 = get();
            c.v = Values.B; Values val2 = get();
            assertEquals(val1, Values.A);
            assertEquals(val2, (isStableEnabled ? Values.A : Values.B));
        }
    }

    /* ==================================================== */

    static class IntStable {
        public @Stable int v;

        public static final IntStable c = new IntStable();
        public static int get() { return c.v; }
        public static void test() throws Exception {
            c.v = 1; int val1 = get();
            c.v = 2; int val2 = get();
            assertEquals(val1, 1);
            assertEquals(val2, (isStableEnabled ? 1 : 2));
        }
    }

    /* ==================================================== */
    // @Stable array == field && all components are stable

    static class ObjectArrayDim1 {
        public @Stable Object[] v;

        public static final ObjectArrayDim1 c = new ObjectArrayDim1();
        public static Object get() { return c.v[0]; }
        public static Object[] get1() { return c.v; }
        public static void test() throws Exception {
            c.v = new Object[1]; c.v[0] = Values.A; Object val1 = get();
                                 c.v[0] = Values.B; Object val2 = get();
            assertEquals(val1, Values.A);
            assertEquals(val2, (isStableEnabled ? Values.A : Values.B));

            c.v = new Object[1]; Object[] arr1 = get1();
            c.v = new Object[1]; Object[] arr2 = get1();
            assertTrue(isStableEnabled ? (arr1 == arr2) : (arr1 != arr2));
        }
    }

    /* ==================================================== */
    // Helpers

    static boolean failed = false;

    public static void run(Class<?> test) {
        Throwable detected = null;
        try {
            test.getDeclaredMethod("test").invoke(null);
        } catch (Throwable t) {
            detected = (t.getCause() != null) ? t.getCause() : t;
        }
        if (detected != null) {
            failed = true;
            System.out.println(test.getName() + ": FAILED");
            detected.printStackTrace(System.out);
        } else {
            System.out.println(test.getName() + ": PASSED");
        }
    }

    static void assertEquals(Object i, Object j) {
        if (i == null ? j != null : !i.equals(j)) {
            throw new AssertionError("Equals check failed: " + i + " != " + j);
        }
    }

    static void assertTrue(boolean b) {
        if (!b) {
            throw new AssertionError("assertTrue failed");
        }
    }
}
