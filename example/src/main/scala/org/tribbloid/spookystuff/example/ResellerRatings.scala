package org.tribbloid.spookystuff.example

import org.apache.spark.{SparkContext, SparkConf}
import org.tribbloid.spookystuff.Conf
import org.tribbloid.spookystuff.entity._
import org.tribbloid.spookystuff.SpookyContext._

/**
 * Created by peng on 18/06/14.
 */
object ResellerRatings {

  def main(args: Array[String]) {

    val conf = new SparkConf().setAppName("ResellerRatings")
    val sc = new SparkContext(conf)

    (sc.parallelize(Seq("Hewlett_Packard")) +>
      Wget("http://www.resellerratings.com/store/#{_}") !!!)
          //remember jsoup doesn't support double quotes in attribute selector!
    .wgetInsertPagination("div#survey-header ul.pagination a:contains(next)")
    .save()
    .collect().foreach(println(_))

    sc.stop()
  }
}
