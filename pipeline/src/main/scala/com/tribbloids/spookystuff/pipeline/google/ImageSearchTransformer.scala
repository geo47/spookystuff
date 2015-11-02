package com.tribbloids.spookystuff.pipeline.google

import java.util.UUID

import com.tribbloids.spookystuff.actions._
import com.tribbloids.spookystuff.pipeline.SpookyTransformer
import com.tribbloids.spookystuff.sparkbinding.PageRowRDD
import com.tribbloids.spookystuff.{SpookyContext, dsl}

class ImageSearchTransformer(
                              override val uid: String =
                              classOf[ImageSearchTransformer].getCanonicalName + "_" + UUID.randomUUID().toString
                              ) extends SpookyTransformer {

  import dsl._
  import org.apache.spark.ml.param._

  /**
   * Param for input column name.
   * @group param
   */
  final val InputCol: Param[Symbol] = new Param[Symbol](this, "inputCol", "input column name")
  final val ImageUrisCol: Param[Symbol] = new Param[Symbol](this, "ImageUrisCol", "output ImageUrisCol column name")
  //TODO: add scrolling down

  setDefault(ImageUrisCol -> null)
  setExample(InputCol -> '_, ImageUrisCol -> 'uris)

  override def exampleInput(spooky: SpookyContext): PageRowRDD = spooky.create(Seq("Giant Robot"))

  override def transform(dataset: PageRowRDD): PageRowRDD = {

    dataset.fetch(
      Visit("http://images.google.com/")
        +> TextInput("input[name=\"q\"]",getOrDefault(InputCol))
        +> Submit("input[name=\"btnG\"]")
    ).select(
      S"div#search img".srcs ~ getOrDefault(ImageUrisCol)
    )
  }
}