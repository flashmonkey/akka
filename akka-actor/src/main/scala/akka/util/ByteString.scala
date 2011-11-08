package akka.util

import java.nio.ByteBuffer
import java.nio.charset.Charset

import scala.collection.IndexedSeqOptimized
import scala.collection.mutable.{ Builder, ArrayBuilder }
import scala.collection.immutable.IndexedSeq
import scala.collection.generic.{ CanBuildFrom, GenericCompanion }

object ByteString {

  def apply(bytes: Array[Byte]): ByteString = ByteString1(bytes.clone)

  def apply(bytes: Byte*): ByteString = {
    val ar = new Array[Byte](bytes.size)
    bytes.copyToArray(ar)
    ByteString1(ar)
  }

  def apply[T](bytes: T*)(implicit num: Integral[T]): ByteString =
    ByteString1(bytes.map(x ⇒ num.toInt(x).toByte)(collection.breakOut))

  def apply(bytes: ByteBuffer): ByteString = {
    val ar = new Array[Byte](bytes.remaining)
    bytes.get(ar)
    ByteString1(ar)
  }

  def apply(string: String): ByteString = apply(string, "UTF-8")

  def apply(string: String, charset: String): ByteString = ByteString1(string.getBytes(charset))

  val empty: ByteString = ByteString1(Array.empty[Byte])

  def newBuilder: Builder[Byte, ByteString] = new ArrayBuilder.ofByte mapResult apply

  implicit def canBuildFrom = new CanBuildFrom[TraversableOnce[Byte], Byte, ByteString] {
    def apply(from: TraversableOnce[Byte]) = newBuilder
    def apply() = newBuilder
  }

  private object ByteString1 {
    def apply(bytes: Array[Byte]) = new ByteString1(bytes)
  }

  final class ByteString1 private (private val bytes: Array[Byte], private val startIndex: Int, val length: Int) extends ByteString {

    private def this(bytes: Array[Byte]) = this(bytes, 0, bytes.length)

    def apply(idx: Int): Byte = bytes(checkRangeConvert(idx))

    private def checkRangeConvert(index: Int) = {
      if (0 <= index && length > index)
        index + startIndex
      else
        throw new IndexOutOfBoundsException(index.toString)
    }

    def toArray: Array[Byte] = {
      val ar = new Array[Byte](length)
      Array.copy(bytes, startIndex, ar, 0, length)
      ar
    }

    override def clone: ByteString = new ByteString1(toArray)

    def compact: ByteString =
      if (length == bytes.length) this else clone

    def asByteBuffer: ByteBuffer = {
      val buffer = ByteBuffer.wrap(bytes, startIndex, length).asReadOnlyBuffer
      if (buffer.remaining < bytes.length) buffer.slice
      else buffer
    }

    def decodeString(charset: String): String =
      new String(if (length == bytes.length) bytes else toArray, charset)

    def ++(that: ByteString): ByteString = that match {
      case b: ByteString1  ⇒ ByteStrings(this, b)
      case bs: ByteStrings ⇒ ByteStrings(this, bs)
    }

    override def slice(from: Int, until: Int): ByteString = {
      val newStartIndex = math.max(from, 0) + startIndex
      val newLength = math.min(until, length) - from
      if (newLength <= 0) ByteString.empty
      else new ByteString1(bytes, newStartIndex, newLength)
    }

    override def copyToArray[A >: Byte](xs: Array[A], start: Int, len: Int): Unit =
      Array.copy(bytes, startIndex, xs, start, math.min(math.min(length, len), xs.length - start))

  }

  private object ByteStrings {
    def apply(bytestrings: Vector[ByteString1]): ByteString = new ByteStrings(bytestrings)

    def apply(b1: ByteString1, b2: ByteString1): ByteString = compare(b1, b2) match {
      case 3 ⇒ new ByteStrings(Vector(b1, b2))
      case 2 ⇒ b2
      case 1 ⇒ b1
      case 0 ⇒ ByteString.empty
    }

    def apply(b: ByteString1, bs: ByteStrings): ByteString = compare(b, bs) match {
      case 3 ⇒ new ByteStrings(b +: bs.bytestrings)
      case 2 ⇒ bs
      case 1 ⇒ b
      case 0 ⇒ ByteString.empty
    }

    def apply(bs: ByteStrings, b: ByteString1): ByteString = compare(bs, b) match {
      case 3 ⇒ new ByteStrings(bs.bytestrings :+ b)
      case 2 ⇒ b
      case 1 ⇒ bs
      case 0 ⇒ ByteString.empty
    }

    def apply(bs1: ByteStrings, bs2: ByteStrings): ByteString = compare(bs1, bs2) match {
      case 3 ⇒ new ByteStrings(bs1.bytestrings ++ bs2.bytestrings)
      case 2 ⇒ bs2
      case 1 ⇒ bs1
      case 0 ⇒ ByteString.empty
    }

    // 0: both empty, 1: 2nd empty, 2: 1st empty, 3: neither empty
    def compare(b1: ByteString, b2: ByteString): Int =
      if (b1.length == 0)
        if (b2.length == 0) 0 else 2
      else if (b2.length == 0) 1 else 3
  }

  final class ByteStrings private (private val bytestrings: Vector[ByteString1]) extends ByteString {

    def apply(idx: Int): Byte =
      if (0 <= idx && idx < length) {
        var pos = 0
        var seen = 0
        while (idx >= seen + bytestrings(pos).length) {
          seen += bytestrings(pos).length
          pos += 1
        }
        bytestrings(pos)(idx - seen)
      } else throw new IndexOutOfBoundsException(idx.toString)

    val length: Int = (0 /: bytestrings)(_ + _.length)

    override def slice(from: Int, until: Int): ByteString = {
      val start = math.max(from, 0)
      val end = math.min(until, length)
      if (end <= start)
        ByteString.empty
      else {
        val iter = bytestrings.iterator
        var cur = iter.next
        var pos = 0
        var seen = 0
        while (from >= seen + cur.length) {
          seen += cur.length
          pos += 1
          cur = iter.next
        }
        val startpos = pos
        val startidx = start - seen
        while (until > seen + cur.length) {
          seen += cur.length
          pos += 1
          cur = iter.next
        }
        val endpos = pos
        val endidx = end - seen
        if (startpos == endpos)
          cur.slice(startidx, endidx)
        else {
          val first = bytestrings(startpos).drop(startidx).asInstanceOf[ByteString1]
          val last = cur.take(endidx).asInstanceOf[ByteString1]
          if ((endpos - startpos) == 1)
            new ByteStrings(Vector(first, last))
          else
            new ByteStrings(first +: bytestrings.slice(startpos + 1, endpos) :+ last)
        }
      }
    }

    def ++(that: ByteString): ByteString = that match {
      case b: ByteString1  ⇒ ByteStrings(this, b)
      case bs: ByteStrings ⇒ ByteStrings(this, bs)
    }

    def compact: ByteString = {
      val ar = new Array[Byte](length)
      var pos = 0
      bytestrings foreach { b ⇒
        b.copyToArray(ar, pos, b.length)
        pos += b.length
      }
      ByteString1(ar)
    }

    def asByteBuffer: ByteBuffer = compact.asByteBuffer

    def decodeString(charset: String): String = compact.decodeString(charset)

  }

}

sealed trait ByteString extends IndexedSeq[Byte] with IndexedSeqOptimized[Byte, ByteString] {
  override protected[this] def newBuilder = ByteString.newBuilder
  def ++(that: ByteString): ByteString
  def compact: ByteString
  def asByteBuffer: ByteBuffer
  final def toByteBuffer: ByteBuffer = ByteBuffer.wrap(toArray)
  final def utf8String: String = decodeString("UTF-8")
  def decodeString(charset: String): String
  final def mapI(f: Byte ⇒ Int): ByteString = map(f andThen (_.toByte))
}
