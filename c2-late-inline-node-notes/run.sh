#!/bin/bash
# Pure-Java repro. usage: [EXTRA_OPTS=...] run.sh [warmMs] [hotMs]
set -e
cd "$(dirname "$0")"
J=${JAVA_HOME_REPRO:-$HOME/.sdkman/candidates/java/25.0.4-tem}
AP=${AP:-/opt/homebrew/lib/libasyncProfiler.dylib}
# Force the scalac-observed inlining shape (everything from apply down inlined into make);
# left to heuristics, C2 often compiles from() standalone first and then refuses to
# inline it ("already compiled into a big method").
INLINE=""
for m in 'SetFactory::apply' 'SetFactory::from' 'SetBuilder::addAll' 'Growable::addAll' 'ArrayIterator::next' 'Runtime::arrayApply'; do
  INLINE="$INLINE -XX:CompileCommand=inline,JRepro\$$m"
done
mkdir -p out/classes
$J/bin/javac -d out/classes JRepro.java
OPTS="-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:CompileCommand=quiet -XX:CompileCommand=dontinline,JRepro\$Runtime::wrapRefArray $INLINE -cp out/classes ${EXTRA_OPTS}"
$J/bin/java -version 2>&1 | head -1
$J/bin/java $OPTS -agentpath:$AP=start,event=cpu,interval=1ms,file=out/ap.jfr JRepro "$@"
$J/bin/java $OPTS -XX:StartFlightRecording=filename=out/jdk.jfr,settings=profile,jdk.ExecutionSample#period=1ms JRepro "$@" 2>&1 | grep -v 'Started recording\|Use jcmd\|jfr,startup'
cp Leaf.java out/Leaf.java
$J/bin/java out/Leaf.java out/ap.jfr 12 14
$J/bin/java out/Leaf.java out/jdk.jfr 12 14
