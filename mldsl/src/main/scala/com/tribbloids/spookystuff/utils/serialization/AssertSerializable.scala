package com.tribbloids.spookystuff.utils.serialization

import java.nio.ByteBuffer

import com.tribbloids.spookystuff.utils.TreeThrowable
import org.apache.spark.serializer.Serializer

import scala.reflect.ClassTag
import scala.util.Try

object AssertSerializable {

  def apply[T <: AnyRef: ClassTag](
      element: T,
      serializers: Seq[Serializer] = SerDeOverride.Default.allSerializers,
      condition: (T, T) => Any = { (v1: T, v2: T) =>
        assert((v1: T) == (v2: T), s"value after deserialization is different: $v1 != $v2")
        assert(v1.toString == v2.toString, s"value.toString after deserialization is different: $v1 != $v2")
        if (!v1.getClass.getCanonicalName.endsWith("$"))
          assert(!(v1 eq v2))
      }
  ): Unit = {

    AssertWeaklySerializable(element, serializers, condition)
  }
}

case class AssertWeaklySerializable[T <: AnyRef: ClassTag](
    element: T,
    serializers: Seq[Serializer] = SerDeOverride.Default.allSerializers,
    condition: (T, T) => Any = { (v1: T, v2: T) =>
      true
    }
) {

  val trials = serializers.map { ser =>
    Try {
      val serInstance = ser.newInstance()
      val binary: ByteBuffer = serInstance.serialize(element)
      assert(binary.array().length > 8) //min object overhead length
      val element2 = serInstance.deserialize[T](binary)
      //      assert(!element.eq(element2))
      condition(element, element2)
    }
  }

  TreeThrowable.&&&(trials)
}
