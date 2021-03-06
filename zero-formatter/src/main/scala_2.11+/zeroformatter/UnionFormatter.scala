package zeroformatter

import java.nio.ByteBuffer
import shapeless._
import shapeless.ops.hlist._
import BinaryUtil._
import Formatter._

object UnionFormatter {

  private[this] object writeUnion extends Poly2 {
    implicit def default[T <: Union[_]](implicit F: Formatter[T]) =
      at[(Array[Byte], Int, Int), T] {
        case ((bytes, offset, byteSize), value) =>
          val v = value.serializeKey(bytes, offset)
          val result = F.serialize(v.value, offset + v.byteSize, value)
          (result.value, offset + v.byteSize + result.byteSize, byteSize + v.byteSize + result.byteSize)
      }
  }

  private[this] case class UnionFormatterResult[C <: Coproduct, H <: HList](fake: C, value: H)

  private[this] object genUnionFormatter extends Poly2 {
    implicit def gen[
      N <: Nat, C <: Coproduct, H <: HList, V <: Union[_],
      K <: HList
    ](implicit
      a: ops.coproduct.At.Aux[C, N, V],
      V: Formatter[V],
      gen: Generic.Aux[V, K],
      init: FillWith[zero.type, K]
    ) =
      at[N, UnionFormatterResult[C, H]]{ case (_, acc) =>
        val kf = gen.from(HList.fillWith[K](zero))
        UnionFormatterResult(acc.fake, (kf, V) :: acc.value)
      }
  }

  private[this] case class ReadUnionResult[T](buf: ByteBuffer, offset: Int, value: Option[T])

  private[this] object readUnion extends Poly2 {
    implicit def read[V <: Union[_], U <: Union[_]] =
      at[ReadUnionResult[U], (V, Formatter[V])] {
        case (acc, (kf, vf)) => acc.value match {
          case Some(_) => acc
          case None => kf.checkKey(acc.buf, acc.offset) match {
            case Some(keyByteSize) =>
              val v = vf.deserialize(acc.buf, acc.offset + keyByteSize)
              acc.copy(value = Some(v.value.asInstanceOf[U]))
            case None => acc
          }
        }
      }
  }

  implicit def unionFormatter[
    A <: Union[_],
    B <: Coproduct,
    C <: Nat, D <: HList, E <: HList
  ](implicit
    gen: Generic.Aux[A, B],
    // serialize
    write: ops.coproduct.LeftFolder.Aux[B, (Array[Byte], Int, Int), writeUnion.type, (Array[Byte], Int, Int)],
    // deserialize
    length: ops.coproduct.Length.Aux[B, C],
    range: ops.nat.Range.Aux[nat._0, C, D],
    generator: RightFolder.Aux[D, UnionFormatterResult[B, HNil], genUnionFormatter.type, UnionFormatterResult[B, E]],
    read: LeftFolder.Aux[E, ReadUnionResult[A], readUnion.type, ReadUnionResult[A]]
  ): Formatter[A] = {

    val fs =
      range()
        .foldRight(UnionFormatterResult(null.asInstanceOf[B], HNil: HNil))(genUnionFormatter)
        .value

    new Formatter[A] {

      override val length = None

      override def serialize(bytes: Array[Byte], offset: Int, value: A) = {
        val values = gen.to(value)
        val (result, _, byteSize) = values.foldLeft((bytes, offset + 4, 4))(writeUnion)
        LazyResult(writeInt(result, offset, byteSize), byteSize)
      }

      override def deserialize(buf: ByteBuffer, offset: Int) = {
        val byteSize = intFormatter.deserialize(buf, offset).value
        if(byteSize == -1) LazyResult(null.asInstanceOf[A], byteSize)
        val result = fs.foldLeft(ReadUnionResult(buf, offset + 4, None: Option[A]))(readUnion)
        LazyResult(result.value.getOrElse(null.asInstanceOf[A]), byteSize)
      }
    }
  }
}
