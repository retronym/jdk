#!/bin/bash
# One JVM: async-profiler with leaf PC addresses + disassembly of the nmethods, so PCs line up.
set -e
cd "$(dirname "$0")"
J=${JAVA_HOME_REPRO:-$HOME/.sdkman/candidates/java/25.0.4-tem}
AP=${AP:-/opt/homebrew/lib/libasyncProfiler.dylib}
INLINE=""
for m in 'SetFactory::apply' 'SetFactory::from' 'SetBuilder::addAll' 'Growable::addAll' 'ArrayIterator::next' 'Runtime::arrayApply'; do
  INLINE="$INLINE -XX:CompileCommand=inline,JRepro\$$m"
done
$J/bin/javac -d out/classes JRepro.java
DYLD_LIBRARY_PATH=${HSDIS_DIR:?set HSDIS_DIR to a directory containing hsdis-<arch> library} $J/bin/java -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
  -XX:CompileCommand=quiet -XX:CompileCommand=dontinline,JRepro\$Runtime::wrapRefArray $INLINE \
  -XX:CompileCommand=print,JRepro::main -XX:CompileCommand=print,JRepro::make -XX:PrintAssemblyOptions=hsdis-print-bytes \
  -agentpath:$AP=start,event=cpu,interval=1ms,features=pcaddr,file=out/pc.jfr \
  -cp out/classes JRepro "$@" > out/asm.txt 2>&1
grep -c 'Compiled method' out/asm.txt
