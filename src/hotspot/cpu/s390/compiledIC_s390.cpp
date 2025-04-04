/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016, 2019 SAP SE. All rights reserved.
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
 *
 */

#include "asm/macroAssembler.inline.hpp"
#include "code/compiledIC.hpp"
#include "code/nmethod.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#ifdef COMPILER2
#include "opto/matcher.hpp"
#endif

// ----------------------------------------------------------------------------

#undef  __
#define __ masm->

address CompiledDirectCall::emit_to_interp_stub(MacroAssembler *masm, address mark/* = nullptr*/) {
#ifdef COMPILER2
  // Stub is fixed up when the corresponding call is converted from calling
  // compiled code to calling interpreted code.
  if (mark == nullptr) {
    // Get the mark within main instrs section which is set to the address of the call.
    mark = __ inst_mark();
  }
  assert(mark != nullptr, "mark must not be null");

  address stub = __ start_a_stub(CompiledDirectCall::to_interp_stub_size());
  if (stub == nullptr) {
    return nullptr;  // CodeBuffer::expand failed.
  }
  __ relocate(static_stub_Relocation::spec(mark));

  AddressLiteral meta = __ allocate_metadata_address(nullptr);
  bool success = __ load_const_from_toc(as_Register(Matcher::inline_cache_reg_encode()), meta);

  __ set_inst_mark();
  AddressLiteral a((address)-1);
  success = success && __ load_const_from_toc(Z_R1, a);
  if (!success) {
    return nullptr;  // CodeCache is full.
  }

  __ z_br(Z_R1);
  __ end_a_stub(); // Update current stubs pointer and restore insts_end.
  return stub;
#else
  ShouldNotReachHere();
  return nullptr;
#endif
}

#undef __

int CompiledDirectCall::to_interp_stub_size() {
  return 2 * MacroAssembler::load_const_from_toc_size() +
         2; // branch
}

// Relocation entries for call stub, compiled java to interpreter.
int CompiledDirectCall::reloc_to_interp_stub() {
  return 5; // 4 in emit_java_to_interp + 1 in Java_Static_Call
}

void CompiledDirectCall::set_to_interpreted(const methodHandle& callee, address entry) {
  address stub = find_stub();
  guarantee(stub != nullptr, "stub not found");

  // Creation also verifies the object.
  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub + NativeCall::get_IC_pos_in_java_to_interp_stub());
  NativeJump*        jump          = nativeJump_at(method_holder->next_instruction_address());
  verify_mt_safe(callee, entry, method_holder, jump);

  // Update stub.
  method_holder->set_data((intptr_t)callee(), relocInfo::metadata_type);
  jump->set_jump_destination(entry);

  // Update jump to call.
  set_destination_mt_safe(stub);
}

void CompiledDirectCall::set_stub_to_clean(static_stub_Relocation* static_stub) {
  // Reset stub.
  address stub = static_stub->addr();
  assert(stub != nullptr, "stub not found");
  assert(CompiledICLocker::is_safe(stub), "mt unsafe call");
  // Creation also verifies the object.
  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub + NativeCall::get_IC_pos_in_java_to_interp_stub());
  NativeJump*        jump          = nativeJump_at(method_holder->next_instruction_address());
  method_holder->set_data(0, relocInfo::metadata_type);
  jump->set_jump_destination((address)-1);
}

//-----------------------------------------------------------------------------

#ifndef PRODUCT

void CompiledDirectCall::verify() {
  // Verify call.
  _call->verify();
  _call->verify_alignment();

  // Verify stub.
  address stub = find_stub();
  assert(stub != nullptr, "no stub found for static call");
  // Creation also verifies the object.
  NativeMovConstReg* method_holder = nativeMovConstReg_at(stub + NativeCall::get_IC_pos_in_java_to_interp_stub());
  NativeJump*        jump          = nativeJump_at(method_holder->next_instruction_address());

  // Verify state.
  assert(is_clean() || is_call_to_compiled() || is_call_to_interpreted(), "sanity check");
}

#endif // !PRODUCT
