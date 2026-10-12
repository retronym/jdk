# C2: late inlining leaks the call site's JVMS into non-safepoint debug info

## Symptom

With `-XX:+DebugNonSafepoints`, CPU profiles of C2 code attribute samples to inlined frames that cannot execute. Found while profiling the Scala 3 compiler with async-profiler: samples in a constructor initialising an empty `Set[Symbol]()` showed

```
Reporter.<init> > Set$.apply > ... > Set$.from@180 > SetBuilderImpl.addAll > Growable.addAll@42
  > ArrayOps$ArrayIterator.next@46 > ScalaRunTime$.array_apply@45 > BoxesRunTime.boxToInteger@1
```

The set is empty, so `Set$.from` returns at its `knownSize == 0` fast path, and the array is an `Object[]`, so the `int[]` boxing arm of `array_apply` is unreachable twice over.

The JDK's own `jdk.ExecutionSample` shows the same stacks, so the profiler is not at fault: the PcDesc table emitted by C2 is wrong.

## Root cause

With `DebugNonSafepoints`, every new `Node` captures `Compile::default_node_notes()` (`node.cpp`, `Node::Init`). The parser saves and restores the default notes around each (inlined) method it parses (`parse1.cpp`). `CallGenerator::do_late_inline_helper` sets the default notes to the late-inlined call site's JVMS and never restores them:

```c++
    // Setup default node notes to be picked up by the inlining
    Node_Notes* old_nn = C->node_notes_at(call->_idx);
    if (old_nn != nullptr) {
      Node_Notes* entry_nn = old_nn->clone(C);
      entry_nn->set_jvms(jvms);
      C->set_default_node_notes(entry_nn);
    }
```

After the last late inline, every node created for the rest of the compilation (IGVN, loop opts, macro expansion, matcher, register-allocator spill copies) carries the JVMS of that call site. Boxing methods (`Integer.valueOf` etc.) are late-inlined last (`Compile::inline_boxing_calls`), so in practice the leaked scope is usually some `valueOf` call site, and profiles over-report `boxToInteger`-style frames.

`-XX:-EliminateAutoBox` (no boxing late inline) makes the misattribution disappear in the repro, which is consistent with this. The same leak applies to other late inlines (incremental inlining, string opts, MH late inline) whenever they are the last to run.

Related: JDK-8201516 ("DebugNonSafepoints generates incorrect information", fixed in 21) fixed a similar notes-reset problem in the parser; async-profiler issues #732 and #758 report similar "impossible stack" symptoms.

## Evidence

`JRepro.java` is a plain-Java mirror of the Scala library path: `make()` always takes the empty fast path, while a warm-up phase drives the same methods with `int[]` (profile pollution) so C2 compiles the boxing arm into `make()`'s inlined body. `run.sh` force-inlines the chain into `make()` (matching the shape C2 chose for the Scala code; left to heuristics C2 often compiles `from()` standalone first) and keeps `wrapRefArray` out of line so `knownSize` cannot be constant-folded.

Samples under `make()` attributed to the dead `... > Runtime.arrayApply@31 > Runtime.boxToInteger@1` chain (macOS aarch64, 15 s hot phase, 1 ms interval):

| JDK | flags | async-profiler 4.5 | JFR `jdk.ExecutionSample` |
|---|---|---|---|
| 25.0.4 | default | 1,484 / 12,630 (12%) | 1,938 / 10,788 (18%) |
| 25.0.4 | `-XX:-EliminateAutoBox` | 0 / 11,074 | 0 / 7,928 |
| 27 | default | 986 / 12,647 (8%) | 27 / 9,195 (*) |
| mainline 63745d1e5 | default | 1,135 / 12,703 (9%) | 154 / 8,545 (*) |
| mainline + fix | default (2 runs) | 0 / 11,675, 0 / 11,693 | 0 / 8,689, 0 / 8,911 |

(*) In those JFR runs C2 compiled `make()` standalone (`make@10[J]`), so a different nmethod was sampled; the async-profiler run in the same configuration shows the full effect.

`pcrun.sh` records leaf PCs (async-profiler `features=pcaddr`) and prints the nmethods with hsdis in the same JVM. The PrintAssembly scope annotations (which are the PcDesc table) label live instructions with the dead `valueOf` scope, e.g. the `k < 10_000` loop compare in `main`, the `ArraySeq.unsafeArray` / array-length loads of the live `knownSize()`, the `putstatic sink` barrier, and many spills/reloads:

Unpatched mainline, C2 OSR compile of `JRepro::main` (690 instructions, 40 of them carry the leaked `valueOf` scope), the `k < 10_000` loop compare:

```
  0x0000000125746884:   mov   w8, #0x2710
  0x0000000125746888:   cmp   w19, w8     ;*invokestatic valueOf {reexecute=0 rethrow=0 return_oop=0 return_scalarized=0}
                                          ; - JRepro$Runtime::boxToInteger@1 (line 12)
                                          ; - JRepro$Runtime::arrayApply@31 (line 21)
                                          ; - JRepro$ArrayIterator::next@30 (line 36)
                                          ; - JRepro$Growable::addAll@18 (line 64)
                                          ; - JRepro$SetBuilder::addAll@2 (line 68)
                                          ; - JRepro$SetFactory::from@38 (line 82)
                                          ; - JRepro$SetFactory::apply@2 (line 77)
                                          ; - JRepro::make@10 (line 89)
                                          ; - JRepro::main@125 (line 111)
  0x000000012574688c:   b.ge  #0x125746bc0
```

With the fix (identical machine code, 690 instructions, 0 carry the `valueOf` scope):

```
  0x0000000125744cc4:   mov   w8, #0x2710
  0x0000000125744cc8:   cmp   w19, w8
  0x0000000125744ccc:   b.ge  #0x125744ffc  ;*if_icmpge {reexecute=0 rethrow=0 return_oop=0 return_scalarized=0}
                                          ; - JRepro::main@122 (line 111)
```

Other mislabelled instructions include the `ArraySeq.unsafeArray` / array-length loads of the live `knownSize()`, the `putstatic sink` GC barrier, and spills/reloads (`str x10, [sp, #0x10]`). Every hot sample PC resolves exactly as these annotations say (first PcDesc at or after the PC), so the profiler's lookup is consistent; the table is wrong.

## Fix

Restore the previous default node notes when `do_late_inline_helper` returns, as the parser does after parsing an inlined method (`PreserveDefaultNodeNotes` in `callGenerator.cpp`). Only debug info changes; the generated code is identical in the repro.

Not done yet: a jtreg regression test (e.g. along the lines of JDK-8201516's `TestDebugInfo`, or checking that no PcDesc outside a late-inlined callee carries its call-site JVMS), and running tier1 compiler tests.

## Reproducing

```
./run.sh [warmMs] [hotMs]              # async-profiler + JFR, prints leaf-frame histograms under make()
HSDIS_DIR=... ./pcrun.sh 3000 5000     # out/asm.txt + out/pc.jfr; java Pc.java out/pc.jfr 30
```

`JAVA_HOME_REPRO` selects the JDK, `AP` the async-profiler library, `EXTRA_OPTS` adds JVM flags (e.g. `-XX:-EliminateAutoBox`).

This investigation, repro and patch were developed with an LLM coding assistant (Claude).
