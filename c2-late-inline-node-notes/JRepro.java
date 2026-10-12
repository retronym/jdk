// Pure-Java mirror of the Scala stdlib path behind `Set[AnyRef]()`:
//   Set$.apply -> Set$.from -> (knownSize == 0 ? empty : builder.addAll)
//   -> Growable.addAll -> ArrayIterator.next -> ScalaRunTime.array_apply -> BoxesRunTime.boxToInteger
// make() always hits the knownSize == 0 fast path, so nothing below `from`'s
// builder branch can execute in the hot phase.
public class JRepro {
  interface IterableOnce { int knownSize(); Iter iterator(); }
  interface Iter { boolean hasNext(); Object next(); }

  // ScalaRunTime.array_apply / BoxesRunTime.boxToInteger
  static final class Runtime {
    static Integer boxToInteger(int i) { return Integer.valueOf(i); }
    static int arrayLength(Object xs) {
      if (xs instanceof Object[] a) return a.length;
      if (xs instanceof int[] a) return a.length;
      if (xs instanceof long[] a) return a.length;
      throw new IllegalArgumentException();
    }
    static Object arrayApply(Object xs, int idx) {
      if (xs instanceof Object[] a) return a[idx];
      if (xs instanceof int[] a) return boxToInteger(a[idx]);
      if (xs instanceof long[] a) return Long.valueOf(a[idx]);
      throw new IllegalArgumentException();
    }
    // ScalaRunTime.wrapRefArray
    static ArraySeq wrapRefArray(Object[] xs) { return new ArraySeq(xs); }
  }

  // ArrayOps.ArrayIterator (generic over primitive arrays via Runtime)
  static final class ArrayIterator implements Iter, IterableOnce {
    final Object xs; int pos;
    ArrayIterator(Object xs) { this.xs = xs; }
    public boolean hasNext() { return pos < Runtime.arrayLength(xs); }
    public Object next() {
      if (pos >= Runtime.arrayLength(xs)) throw new java.util.NoSuchElementException();
      Object r = Runtime.arrayApply(xs, pos); pos += 1; return r;
    }
    public int knownSize() { return Runtime.arrayLength(xs) - pos; }
    public Iter iterator() { return this; }
  }

  // ArraySeq.ofRef
  static final class ArraySeq implements IterableOnce {
    final Object[] unsafeArray;
    ArraySeq(Object[] a) { unsafeArray = a; }
    public int knownSize() { return unsafeArray.length; }
    public Iter iterator() { return new ArrayIterator(unsafeArray); }
  }

  // Extra shapes so `it.knownSize()` in from() is megamorphic
  static final class ListLike implements IterableOnce {
    final java.util.List<Object> l; ListLike(java.util.List<Object> l) { this.l = l; }
    public int knownSize() { return -1; }
    public Iter iterator() { var i = l.iterator(); return new Iter() { public boolean hasNext() { return i.hasNext(); } public Object next() { return i.next(); } }; }
  }
  static final class RangeLike implements IterableOnce {
    final int n; RangeLike(int n) { this.n = n; }
    public int knownSize() { return n; }
    public Iter iterator() { return new ArrayIterator(new int[] {0, 1, 2}); }
  }

  // Growable.addAll / SetBuilderImpl
  interface Growable { void addOne(Object x);
    default Growable addAll(IterableOnce xs) { Iter it = xs.iterator(); while (it.hasNext()) addOne(it.next()); return this; } }
  static final class SetBuilder implements Growable {
    final java.util.HashSet<Object> elems = new java.util.HashSet<>();
    public void addOne(Object x) { elems.add(x); }
    public Growable addAll(IterableOnce xs) { return Growable.super.addAll(xs); }
    Object result() { return elems; }
  }

  // immutable.Set$
  static final class SetFactory {
    static final Object EMPTY = new Object();
    static final SetFactory MODULE = new SetFactory();
    Object empty() { return EMPTY; }
    Object apply(IterableOnce elems) { return from(elems); }
    Object from(IterableOnce it) {
      if (it.knownSize() == 0) return empty();
      if (it instanceof java.util.Set<?> s) return s;
      SetBuilder b = new SetBuilder();
      b.addAll(it);
      return b.result();
    }
  }

  static Object sink;

  static Object make() { return SetFactory.MODULE.apply(Runtime.wrapRefArray(new Object[0])); }

  static final IterableOnce[] SHAPES = {
    new ListLike(java.util.List.of("x", "y")), new RangeLike(3), new ArraySeq(new Object[] {"p"}),
  };
  static Object pollute(int i) {
    Object r = SetFactory.MODULE.from(new ArrayIterator(new int[] {i, i + 1, i + 2}));
    Object r2 = SetFactory.MODULE.apply(Runtime.wrapRefArray(new Object[] {"a" + (i & 7), "b"}));
    Object r3 = SetFactory.MODULE.from(SHAPES[i % 3]);
    Object r4 = SetFactory.MODULE.from(new ArrayIterator(new long[] {i}));
    return (r2.hashCode() ^ r3.hashCode() ^ r4.hashCode()) == 0 ? r2 : r;
  }

  public static void main(String[] args) {
    long warmMs = Long.parseLong(args.length > 0 ? args[0] : "3000");
    long hotMs = Long.parseLong(args.length > 1 ? args[1] : "15000");
    long end = System.currentTimeMillis() + warmMs;
    int i = 0;
    while (System.currentTimeMillis() < end) { sink = pollute(i++); if ((i & 63) == 0) sink = make(); }
    System.err.println("warmup done, polluted " + i);
    end = System.currentTimeMillis() + hotMs;
    long n = 0;
    while (System.currentTimeMillis() < end) { for (int k = 0; k < 10_000; k++) sink = make(); n++; }
    System.err.println("hot done " + n * 10_000);
  }
}
