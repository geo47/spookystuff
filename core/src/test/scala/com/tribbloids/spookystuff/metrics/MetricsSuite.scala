package com.tribbloids.spookystuff.metrics

import com.tribbloids.spookystuff.metrics.MetricsSuite.{DummyMetrics, DummyTreeMetrics}
import com.tribbloids.spookystuff.testutils.{FunSpecx, TestHelper}
import org.apache.spark.ml.dsl.utils.messaging.MessageWriter
import org.apache.spark.sql.execution.streaming.EventTimeStatsAccum
import org.apache.spark.util.{DoubleAccumulator, LongAccumulator}

object MetricsSuite {

  case class DummyMetrics(
      v1: Acc[LongAccumulator] = "v1" -> 0L,
      v2: Acc[DoubleAccumulator] = "v2" -> 1.0
  ) extends Metrics

  case class DummyTreeMetrics(
      v3: Acc[EventTimeStatsAccum] = "v3" -> 2L,
      sub: DummyMetrics = DummyMetrics()
  ) extends Metrics
}

class MetricsSuite extends FunSpecx {

  TestHelper.TestSC

  it("can be converted to JSON") {
    val m = DummyMetrics()
    m.toNestedMap
      .toJSON()
      .shouldBe(
        """
          |{
          |  "v2" : 1.0,
          |  "v1" : 0
          |}
        """.stripMargin
      )
  }

  it("tree can be converted to JSON") {
    val m = DummyTreeMetrics()
    m.toNestedMap
      .toJSON()
      .shouldBe(
        """
          |{
          |  "sub" : {
          |    "v2" : 1.0,
          |    "v1" : 0
          |  },
          |  "v3" : {
          |    "max" : 2,
          |    "min" : 2,
          |    "avg" : 2.0,
          |    "count" : 1
          |  }
          |}
        """.stripMargin
      )

    MessageWriter(m.toMap)
      .toJSON()
      .shouldBe(
        """
          |{
          |  "v3" : {
          |    "max" : 2,
          |    "min" : 2,
          |    "avg" : 2.0,
          |    "count" : 1
          |  },
          |  "sub/v2" : 1.0,
          |  "sub/v1" : 0
          |}
        """.stripMargin
      )
  }
}
