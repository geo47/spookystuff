package com.tribbloids.spookystuff.utils

import java.util.concurrent.ArrayBlockingQueue

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.utils.SparkHelper

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class PreemptiveLocalOps(capacity: Int)(
    implicit exeCtx: ExecutionContext = PartitionExecution.exeCtx
) {

  import SpookyViews._

  trait Impl[T] {

    def partitionFactories: Seq[() => PartitionExecution[T]]

    lazy val wIndex: Seq[(() => PartitionExecution[T], Int)] = partitionFactories.zipWithIndex

    def sc: SparkContext

    def toLocalPartitionIterator: Iterator[Array[T]] = SparkHelper.withScope(sc) {

      val buffer = new ArrayBlockingQueue[Try[PartitionExecution[T]]](capacity)

      val p = SparkLocalProperties(sc)

      Future {

        sc.setJobGroup(p.groupID, p.description)

        wIndex.foreach {
          case (factory, ii) =>
            val exe = factory()

            val jobText = exe.jobTextOvrd.getOrElse(
              s"$ii (preemptive)"
            )

            sc.withJob(jobText) {
              buffer.put(Success(exe)) // may be blocking due to capacity
              exe.AsArray.start // non-blocking
            }
        }
      }.onFailure {
        case e: Throwable =>
          buffer.put(Failure(e))
      }

      wIndex.toIterator.map { _ =>
        val exe = buffer.take().get
        exe.AsArray.get
      }
    }

    def toLocalIterator: Iterator[T] = {

      toLocalPartitionIterator.flatMap(v => v.iterator)
    }
  }

  case class ForRDD[T](self: RDD[T]) extends Impl[T] {

    def sc: SparkContext = self.sparkContext

    override lazy val partitionFactories: Seq[() => PartitionExecution[T]] = {

      self.partitions.toSeq.map(_.index).map { i => () =>
        PartitionExecution[T](self, i)
      }
    }
  }

  case class ForDataset[T](self: Dataset[T]) extends Impl[T] {

    def sc: SparkContext = self.sparkSession.sparkContext

    lazy val delegate: ForRDD[T] = ForRDD(self.rdd)

    override def partitionFactories: Seq[() => PartitionExecution[T]] = delegate.partitionFactories
  }

}
