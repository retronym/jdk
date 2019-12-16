/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary ciMethod::is_tiny_inline should cover forwarder methods
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 *
 * @run driver compiler.inlining.InlineForwarders
 */

package compiler.inlining;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class InlineForwarders {
    public static void main(String[] args) throws Exception {
        // try some sanity checks first
        doTest();

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+IgnoreUnrecognizedVMOptions", "-showversion",
                "-server", "-XX:-TieredCompilation", "-Xbatch",
                "-XX:+PrintCompilation", "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining",
                "-XX:CompileCommand=dontinline,compiler.inlining.InlineForwarders::underlying*",
                "-XX:CompileCommand=dontinline,compiler.inlining.InlineForwarders::doTest*",
                "-XX:MaxInlineLevel=0",
                Launcher.class.getName());

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);
        // The test is applicable only to C2 (present in Server VM).
        if (analyzer.getStderr().contains("Server VM")) {
            analyzer.shouldMatch("InlineForwarders::forward1 \\(\\d+ bytes\\)   tiny when inlined");
            analyzer.shouldMatch("InlineForwarders::forward2a \\(\\d+ bytes\\)   tiny when inlined");
            analyzer.shouldNotMatch("InlineForwarders::forwardNok3 \\(\\d+ bytes\\)   tiny when inlined");
        }
    }

    public void underlying() { toString().hashCode(); }
    public void underlying(int a) { toString().hashCode(); }
    public void forward1()   { underlying(); }
    public void forward2a()   { underlying(); }
    public void forward2b()   { forward2a(); }
    public void forwardNok3()   { underlying(); underlying(); }

    static void doTest() {
        InlineForwarders o = new InlineForwarders();
        o.forward1();
        o.forward2b();
        o.forwardNok3();
    }

    static class Launcher {
        public static void main(String[] args) throws Exception {
            for (int c = 0; c < 20_000; c++) {
              doTest();
            }
        }
    }
}
