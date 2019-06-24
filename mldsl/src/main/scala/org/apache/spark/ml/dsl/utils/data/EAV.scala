package org.apache.spark.ml.dsl.utils.data

import java.util.Properties

import com.tribbloids.spookystuff.utils.{CommonUtils, IDMixin, TreeException}
import org.apache.spark.ml.dsl.utils.{Nullable, ScalaNameMixin}
import org.apache.spark.sql.catalyst.util.CaseInsensitiveMap

import scala.collection.immutable.ListMap
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.Try

/**
  * entity-(with)-attribute-value
  */
trait EAV extends Serializable with IDMixin {

  type VV
  protected def _ctg(implicit v: ClassTag[VV]) = v
  implicit def ctg: ClassTag[VV]

  def source: EAV

  final lazy val core: EAV.Impl = source match {
    case c: EAV.Impl => c
    case _           => source.core
  }

  def asOriginalMap: ListMap[String, VV] = {

    val ctg = this.ctg
    core.self.collect {
      case (k, ctg(v)) => k -> v
    }
  }
  lazy val asCaseInsensitiveMap: Map[String, VV] = CaseInsensitiveMap(asOriginalMap)

  def asMap: Map[String, VV] = asOriginalMap
  def asStrMap: Map[String, String] = asMap.mapValues(v => Option(v).map(_.toString).orNull)

  override def _id = asMap

  def asProperties: Properties = {
    val properties = new Properties()

    asStrMap.foreach { v =>
      properties.put(v._1, v._2)
    }

    properties
  }

  /**
    * favor the key-value pair in first operand
    */
  def :++(other: EAV): EAV.Impl = EAV.Impl.fromMap(other.asMap ++ this.asMap)

  /**
    * favor the key-value pair in second operand
    * operands suffixed by : are reversed
    */
  final def ++:(other: EAV): EAV.Impl = {

    :++(other)
  }

  def updated(key: String, value: VV): EAV.Impl = {
    EAV.Impl.fromMap(this.asMap.updated(key, value))
  }

  def updateIfExists(key: String, vOpt: Nullable[VV]): EAV.Impl = {
    vOpt.asOption match {
      case None    => this.core
      case Some(v) => updated(key, v)
    }
  }

  def formattedStr(sep: String = " "): String = {
    asStrMap.map(tuple => s"${tuple._1}=${tuple._2}").mkString(sep)
  }

  lazy val showStr = formattedStr()

  override def toString: String = showStr

  lazy val providedHintStr: Option[String] = {
    if (asStrMap.isEmpty) {
      None
    } else {
      Some(s"only ${asMap.keys.mkString(", ")} are provided")
    }
  }

  //TODO: cleanup for being too redundant! not encouraged to use

  def tryGet(k: String, nullable: Boolean = false): Try[VV] = Try {
    val result = asMap.getOrElse(
      k.toLowerCase(),
      throw new UnsupportedOperationException(
        (
          Seq(
            s"Parameter $k is missing"
          ) ++ providedHintStr
        ).mkString("\n")
      )
    )
    if (!nullable) require(result != null, s"null value for `${k.toLowerCase}`")
    result
  }

  def get(k: String, nullable: Boolean = false): Option[VV] = tryGet(k, nullable).toOption

  def apply(k: String, nullable: Boolean = false): VV = tryGet(k, nullable).get

  def getOrElse(k: String, default: VV): VV = {
    require(
      default != null,
      s"default value for `${k.toLowerCase}` cannot be null"
    )
    get(k).getOrElse(default)
  }

  def contains(k: String): Boolean = tryGet(k).isSuccess

  def attr(v: String) = new AA(primaryNameOverride = v)

  def drop(vs: Magnets.K*): EAV.Impl = EAV.Impl.fromMap(asMap -- vs.flatMap(_.names))

  def dropAll(vs: Iterable[Magnets.K]): EAV.Impl = drop(vs.toSeq: _*)

  def --(vs: Iterable[Magnets.K]): EAV.Impl = dropAll(vs)

  //TODO: support mixing param and map definition? While still being serializable?
  class Attr[T](
      // should only be used in setters
      val aliases: List[String] = Nil,
      nullable: Boolean = false,
      default: Nullable[T] = None,
      primaryNameOverride: Nullable[String] = None
  )(implicit ev: T <:< VV)
      extends AttrLike[T]
      with ScalaNameMixin {

    def outer: EAV = EAV.this

    final def primaryName: String = primaryNameOverride.getOrElse(objectSimpleName)

    private def _getDefaultV: T = default.getOrElse {
      throw new UnsupportedOperationException(s"Undefined default value for $primaryName")
    }

    override lazy val tryGet: Try[T] = {
      val trials: Seq[() => T] = allNames.map { name =>
        { () =>
          outer.apply(name, nullable).asInstanceOf[T]
        }
      } ++ Seq(() => _getDefaultV)

      Try {
        TreeException
          .|||^(trials)
          .get
      }
    }

    def tryGetEnum[EE <: Enumeration](enum: EE)(implicit ev: T <:< String): Try[EE#Value] = {
      tryGet
        .flatMap { v =>
          Try {
            enum.withName(ev(v).toLowerCase())
          }
        }
    }
    def getEnum[EE <: Enumeration](enum: EE)(implicit ev: T <:< String) = tryGetEnum(enum).toOption

    def tryGetBoolean(implicit ev: T <:< String): Try[Boolean] = {
      tryGet.map { v =>
        CommonUtils.tryParseBoolean(v).get
      }
    }
    def getBoolean(implicit ev: T <:< String) = tryGetBoolean.toOption

    def tryGetBoolOrInt(implicit ev: T <:< String): Try[Int] = {

      tryGet
        .map(v => ev(v).toInt)
        .recoverWith {
          case _: Throwable =>
            tryGetBoolean
              .map {
                case true  => 1
                case false => 0
              }
        }
    }
    def getBoolOrInt(implicit ev: T <:< String) = tryGetBoolOrInt.toOption
  }

  object Attr {

    //TODO: is it useless due to being path dependent?
    implicit def fromStr(v: String): Attr[VV] = new Attr[VV](primaryNameOverride = v)
  }

  type AA = Attr[VV]
}

object EAV extends EAVBuilder[EAV] {

  type Impl = EAVCore
  override def Impl = EAVCore

  trait ImplicitSrc extends EAV

  def empty: Impl = Impl.proto
}