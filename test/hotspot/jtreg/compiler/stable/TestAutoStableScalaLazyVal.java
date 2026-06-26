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
 * @test TestAutoStableScalaLazyVal
 * @summary -Xauto-stable-scala-lazy-val marks a field x as @Stable when the
 *          class also declares a method x$lzycompute whose return type is
 *          identical to the field's type (the Scala lazy val shape).  The
 *          test runs on the application class loader (not /bootclasspath) and
 *          checks both that the matching field is constant-folded when the
 *          option is enabled, and that non-matching fields and the option-off
 *          case are not folded.
 *
 * @run main/othervm -Xauto-stable-scala-lazy-val -Xcomp
 *                   -XX:CompileOnly=*TestAutoStableScalaLazyVal*::get*
 *                   -XX:-TieredCompilation -XX:+FoldStableValues
 *                   compiler.stable.TestAutoStableScalaLazyVal true
 *
 * @run main/othervm -Xcomp
 *                   -XX:CompileOnly=*TestAutoStableScalaLazyVal*::get*
 *                   -XX:-TieredCompilation -XX:+FoldStableValues
 *                   compiler.stable.TestAutoStableScalaLazyVal false
 */

package compiler.stable;

public class TestAutoStableScalaLazyVal {

    // The Scala lazy val shape: cache field x, a bitmap, and the computing
    // method x$lzycompute() whose return type is identical to x's type.
    // No @Stable annotation is present; the VM must infer it from the option.
    static class C {
        int x;
        volatile boolean bitmap$0;
        int x$lzycompute() { x = 42; bitmap$0 = true; return x; }
    }

    // Negative: the co-declared method returns a different type than the field.
    static class D {
        int y;
        long y$lzycompute() { y = 7; return y; }
    }

    // Negative: there is no co-declared x$lzycompute method at all.
    static class E {
        int z;
        int zCompute() { z = 9; return z; }
    }

    static final C c = new C();
    static final D d = new D();
    static final E e = new E();

    static int getX() { return c.x; }
    static int getY() { return d.y; }
    static int getZ() { return e.z; }

    static boolean failed = false;

    public static void main(String[] args) {
        boolean optionEnabled = Boolean.parseBoolean(args[0]);

        // Guard: must be defined by an application loader, otherwise the test
        // would not distinguish the option from ordinary boot-loader handling.
        if (TestAutoStableScalaLazyVal.class.getClassLoader() == null) {
            throw new Error("Test classes are boot-loaded; cannot validate the "
                          + "any-class-loader behavior of -Xauto-stable-scala-lazy-val.");
        }

        // Only C.x is expected to be folded, and only when the option is on.
        c.x = 1; int x1 = getX();
        c.x = 2; int x2 = getX();
        check("C.x (matches x$lzycompute)", x1, x2, optionEnabled);

        d.y = 1; int y1 = getY();
        d.y = 2; int y2 = getY();
        check("D.y (lzycompute wrong return type)", y1, y2, false);

        e.z = 1; int z1 = getZ();
        e.z = 2; int z2 = getZ();
        check("E.z (no lzycompute method)", z1, z2, false);

        if (failed) {
            throw new Error("TEST FAILED");
        }
    }

    static void check(String name, int v1, int v2, boolean expectFold) {
        boolean folded = (v2 == 1);
        boolean ok = (v1 == 1) && (folded == expectFold);
        System.out.printf("%-40s v1=%d v2=%d folded=%b expectFold=%b %s%n",
                          name, v1, v2, folded, expectFold, ok ? "PASSED" : "FAILED");
        if (!ok) {
            failed = true;
        }
    }
}
