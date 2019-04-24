// #tag::imports[]
import com.couchbase.client.core.error.subdoc.PathExistsException
import com.couchbase.client.scala._
import com.couchbase.client.scala.codec.Conversions.Codec
import com.couchbase.client.scala.implicits.Codecs
import com.couchbase.client.scala.json._
import com.couchbase.client.scala.kv.{LookupInResult, LookupInSpec, MutateInSpec}
import com.couchbase.client.scala.query._
import reactor.core.scala.publisher._
import com.couchbase.client.scala.kv._
import com.couchbase.client.scala.kv.MutateInSpec._
import com.couchbase.client.scala.kv.LookupInSpec._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
// #end::imports[]


object Queries {
  def main(args: Array[String]): Unit = {

// #tag::cluster[]
val cluster = Cluster.connect("localhost", "username", "password")
val bucket = cluster.bucket("travel-sample")
val collection = bucket.defaultCollection
// #end::cluster[]

def getFunc() {
// #tag::get[]
val result: Try[LookupInResult] = collection.lookupIn("customer123", Array(
  get("addresses.delivery.country")
))

result match {
  case Success(r)   =>
    val str: Try[String] = r.contentAs[String](0)

    str match {
      case Success(s)   => println(s"Country: ${s}") // "United Kingdom"
      case Failure(err) => println(s"Error: ${err}")
    }

  case Failure(err) => println(s"Error: ${err}")
}
// #end::get[]

def getFlatMap() {
// #tag::get-flatmap[]
val result: Try[String] = collection.lookupIn("customer123", Array(
  get("addresses.delivery.country")
)).flatMap(result => result.contentAs[String](0))

result match {
  case Success(str) => println(s"Country: ${str}")
  case Failure(err) => println(s"Error: ${err}")
}
// #end::get-flatmap[]
}

def getForComp() {
// #tag::get-forcomp[]
val result: Try[String] = for {
  result <- collection.lookupIn("customer123",
    Array(get("addresses.delivery.country")))
  str    <- result.contentAs[String](0)
} yield str

result match {
  case Success(str) => println(s"Country: ${str}")
  case Failure(err) => println(s"Error: ${err}")
}
// #end::get-forcomp[]
}

def existsFunc() {
// #tag::exists[]
val result: Try[Boolean] = collection.lookupIn("customer123",
  Array(exists("addresses.delivery.does_not_exist")))
  .flatMap(result => result.contentAs[Boolean](0))

result match {
  case Success(exists) => println(s"Does field exist? ${exists}}")
  case Failure(err)    => println(s"Error: ${err}")
}
// #end::exists[]
}

def combine() {
// #tag::combine[]
val result: Try[(String, Boolean)] = for {
  result   <- collection.lookupIn("customer123", Array(
    get("addresses.delivery.country"),
    exists("addresses.delivery.does_not_exist")))
  country  <- result.contentAs[String](0)
  exists   <- result.contentAs[Boolean](1)
} yield (country, exists)

result match {
  case Success((country, exists)) =>
    println(s"Country = ${country}, Exists = ${exists}}")
  case Failure(err)               =>
    println(s"Error: ${err}")
}
// #end::combine[]
}

def future() {
// #tag::get-future[]
val future: Future[LookupInResult] = collection.async.lookupIn("customer123", Array(
  get("addresses.delivery.country")
))

// Just for example, block on the result - this is not best practice
val result: LookupInResult = Await.result(future, Duration.Inf)

result.contentAs[String](0) match {
  case Success(str) => println(s"Country: ${str}")
  case Failure(err) => println(s"Error: ${err}")
}
// #end::get-future[]
}

def reactive() {
// #tag::get-reactive[]
val mono = collection.reactive.lookupIn("customer123", Array(
  get("addresses.delivery.country")
))

// Just for example, block on the result - this is not best practice
val result: Option[LookupInResult] = mono.block()
  
val str: Option[String] = result.flatMap(r =>
  r.contentAs[String](0).toOption)

str match {
  case Some(str) => println(s"Country: ${str}")
  case _         => println(s"Error")
}
// #end::get-reactive[]
}


def upsertFunc() {
// #tag::upsert[]
val result: Try[MutateInResult] = collection.mutateIn("customer123", Array(
  upsert("email", "dougr96@hotmail.com")
))

result match {
  case Success(_)   => println("Success!")
  case Failure(err) => println(s"Error: ${err}")
}
// #end::upsert[]
}

def insertFunc() {
// #tag::insert[]
val result: Try[MutateInResult] = collection.mutateIn("customer123", Array(
  insert("email", "dougr96@hotmail.com")
))

result match {
  case Success(_)                   => println("Unexpected success...")
  case Failure(PathExistsException) => println(s"Error, path already exists")
  case Failure(err)                 => println(s"Error: ${err}")
}
// #end::insert[]
}

def multiFunc() {
// #tag::multi[]
val result = collection.mutateIn("customer123", Array(
  remove("addresses.billing"),
  replace("email", "dougr96@hotmail.com")
))

// Note: for brevity, checking the result will be skipped in subsequent examples, but obviously is necessary in a production application.
// #end::multi[]
}


def arrayAppendFunc() {
// #tag::array-append[]
val result = collection.mutateIn("customer123", Array(
  arrayAppend("purchases.complete", 777)
))

// purchases.complete is now [339, 976, 442, 666, 777]
// #end::array-append[]
}

def arrayPrependFunc() {
// #tag::array-prepend[]
val result = collection.mutateIn("customer123", Array(
  arrayPrepend("purchases.abandoned", 18)
))

// purchases.abandoned is now [18, 157, 49, 999]
// #end::array-prepend[]
}

def createAndPopulateArray() {
// #tag::array-create[]
val result = collection.upsert("my_array", JsonArray.create)
    .flatMap(r =>
      collection.mutateIn("my_array", Array(
        arrayAppend("", "some element")
      ))
    )

// the document my_array is now ["some element"]
// #end::array-create[]
}

def arrayCreate() {
// #tag::array-upsert[]
val result = collection.mutateIn("some_doc", Array(
  arrayAppend("some.array", "hello world").createPath
))
// #end::array-upsert[]
}

def arrayUnique() {
// #tag::array-unique[]
val result1 = collection.mutateIn("customer123", Array(
  arrayAddUnique("purchases.complete", 95)
))

// Just for demo, a production app should check the result properly
assert(result1.isSuccess)

val result2 = collection.mutateIn("customer123", Array(
  arrayAddUnique("purchases.complete", 95)
))

result2 match {
  case Success(_)                   => println("Unexpected success...")
  case Failure(PathExistsException) => println(s"Error, path already exists")
  case Failure(err)                 => println(s"Error: ${err}")
}
// #end::array-unique[]
}

def arrayInsertFunc() {
// #tag::array-insert[]
val result = collection.mutateIn("some_doc", Array(
  arrayInsert("foo.bar[1]", "cruel")
))
// #end::array-insert[]
}

def counterInc() {
// #tag::counter-inc[]
val result = collection.mutateIn("customer123", Array(
  increment("logins", 1)
))

result match {
  case Success(r) =>
    // Counter operations return the updated count
    r.contentAs[Long](0)
      .foreach(count => println(s"After increment counter is ${count}"))
  case Failure(err)  => println(s"Error: ${err}")
}
// #end::counter-inc[]
}

def counterDec() {
// #tag::counter-dec[]
val upsertResult = collection.upsert("player432", JsonObject("gold" -> 1000))

assert (upsertResult.isSuccess)

val result = collection.mutateIn("player432", Array(
  decrement("gold", 150)
))
// #end::counter-dec[]
}

def createPath() {
// #tag::create-path[]
val result = collection.mutateIn("customer123", Array(
  upsert("level_0.level_1.foo.bar.phone", JsonObject(
    "num" -> "311-555-0101",
    "ext" -> 16)).createPath
))
// #end::create-path[]
}

def concurrent() {
// #tag::concurrent[]
// Thread 1
collection.mutateIn("customer123", Array(
  arrayAppend("purchases.complete", 99)
))

// Thread 2
collection.mutateIn("customer123", Array(
  arrayAppend("purchases.abandoned", 101)
))
// #end::concurrent[]


}

def cas() {
// #tag::cas[]
val result = collection.get("player432")
  .flatMap(doc => collection.mutateIn("player432", Array(
    decrement("gold", 150)
  ), cas = doc.cas))
  // #end::cas[]
}

}