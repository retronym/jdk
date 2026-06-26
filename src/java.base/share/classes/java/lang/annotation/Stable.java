/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.lang.annotation;

/**
 * A field may be annotated as <em>stable</em> to indicate that it is a
 * <em>stable variable</em>, expected to change its value at most once.
 * All Java fields are initialized by the VM with a default value
 * (null or zero).  A properly used stable field is set just once to a
 * non-default value, and keeps that value forever.
 * <p>
 * While the field contains its default (null or zero) value, the VM
 * treats it as an ordinary mutable variable.  When a non-default value
 * is stored into the field, the VM is permitted to assume that no more
 * significant changes will occur.  This in turn enables the VM to
 * optimize uses of the stable variable, treating subsequent reads as
 * constant values.  This behavior is a useful building block for lazy
 * evaluation or memoization of results &mdash; for example, the
 * one-time, write-once initialization of a lazily computed value such as
 * a Scala {@code lazy val} or a memoizing supplier.
 *
 * <h2><a id="contract"></a>The stable contract</h2>
 *
 * The optimization described above is only sound if the program obeys
 * the <em>stable contract</em>.  A field annotated {@code @Stable}:
 * <ul>
 * <li>must be written at most once to a non-default value, and</li>
 * <li>must never be reset to the default value (null or zero) once a
 *     non-default value has been observed, and</li>
 * <li>must be safely published, so that any non-default value is visible
 *     without races to readers.  Writing the field from a constructor or
 *     class initializer, or using a release fence / {@link
 *     java.lang.invoke.VarHandle} atomic operation, achieves this.</li>
 * </ul>
 * The VM does <em>not</em> verify the contract.  If the contract is
 * broken &mdash; for example, if a non-default value is overwritten with
 * a different non-default value &mdash; then different parts of the
 * program may observe different values for the field, with no defined
 * ordering, and the behavior of the program is undefined.  In this
 * respect {@code @Stable} is a sharp tool: it offers the constant-folding
 * benefit of a {@code final} field to fields that must be assigned after
 * construction, but it shifts the responsibility for correct, race-free,
 * write-once initialization to the programmer.
 *
 * <h2><a id="arrays"></a>Stable arrays</h2>
 *
 * A stable variable may be an array component as well as a field.  If a
 * stable field is declared as an array type with one dimension, both that
 * array as a whole, and its eventual components, are treated as
 * independent stable variables.  When a reference to an array of length
 * <i>N</i> is stored to the field, the array object itself is taken to be
 * constant, and all <i>N</i> components are <em>also</em> treated as
 * independent stable variables.  More generally, if a stable field is
 * declared as an array type with <em>D</em> dimensions, then all the
 * non-null components of the array, and of any sub-arrays up to a nesting
 * depth less than <em>D</em>, are treated as stable variables.  Because
 * the top-level array reference is itself stable, the array should not be
 * resized once published.
 *
 * <h2><a id="default"></a>The default value is never folded</h2>
 *
 * Because every field starts at its default value, the VM cannot
 * distinguish a stable variable that is still uninitialized from one that
 * was deliberately set to null (or zero).  The VM therefore never
 * constant-folds the default value.  If an application requires constant
 * folding of a value that may legitimately be the default, it should add
 * a level of indirection, for example by boxing the value in a non-null
 * holder.  Consequently, most code that reads a stable variable should
 * test for the default value and fall back to (idempotent) computation
 * when it is observed.
 *
 * @apiNote
 * This is the public counterpart of the long-standing internal
 * annotation {@code jdk.internal.vm.annotation.Stable}, exposed so that
 * libraries outside the JDK can build write-once, lazily initialized data
 * structures that the VM can optimize.  See JEP 401.  Unlike the internal
 * annotation, which is honored only for classes loaded by the boot and
 * platform class loaders, this annotation is honored for fields of
 * classes loaded by any class loader.
 *
 * @since 26
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Stable {
}
