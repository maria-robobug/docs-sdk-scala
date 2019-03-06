// #tag::imports[]
import java.util.UUID

import com.couchbase.client.scala.Cluster
import com.couchbase.client.scala.api.MutationResult
import com.couchbase.client.scala.json.JsonObject

import scala.util.{Failure, Success}
// #end::imports[]

// #tag::cc-create[]
case class Address(line1: String)
case class User(name: String, age: Int, addresses: Seq[Address])
// #end::cc-create[]

// #tag::cc-codec[]
object User {
  implicit val codec: Codec[User] = Codecs.codec[User]
}
// #end::cc-codec[]

class KvOperations {
  // #tag::cluster[]
  val cluster = Cluster.connect("10.112.180.101", "username", "password")
  // #end::cluster[]

  // #tag::resources[]
  val bucket = cluster.bucket("bucket-name")
  val scope = bucket.scope("scope-name")
  val collection = scope.collection("collection-name")
  // #end::resources[]

  // #tag::upsert[]
  val json = JsonObject.create.put("foo", "bar").put("baz", "qux")

  collection.upsert("document-key", json) match {
    case Success(result) =>
    case Failure(exception) => println("Error: " + exception)
  }
  // #end::upsert[]

  def insert() {
    // #tag::insert[]
    collection.insert("document-key", json) match {
      case Success(result) =>
      case Failure(err: DocumentAlreadyExistsException) =>
        println("The document already exists")
      case Failure(err) => println("Error: " + err)
    }
    // #end::insert[]
  }

  // #tag::get-simple[]
  collection.get("document-key") match {
    case Success(result) => println("Document fetched successfully")
    case Failure(err) => println("Error getting document: " + err)
  }
  // #end::get-simple[]

  def get() {
    // #tag::get[]
    // Create some initial JSON
    val json = JsonObject.create.put("status", "awesome!")

    // Insert it
    collection.insert("document-key", json) match {
      case Success(result) =>
      case Failure(err) => println("Error: " + err)
    }

    // Get it back
    collection.get("document-key") match {
      case Success(result) =>

        // Convert the content to a JsonObject
        result.contentAs[JsonObjectSafe] match {
          case Success(json) =>

            // Pull out the JSON's status field, if it exists
            json.str("status") match {
              case Success(status) => println(s"Couchbase is $status")
              case _ => println("Field 'status' did not exist")
            }
          case Failure(err) => println("Error decoding result: " + err)
        }
      case Failure(err) => println("Error getting document: " + err)
    }
    // #end::get[]
  }

  // #tag::get-for[]
  (for {
    result <- collection.get("document-key")
    json   <- result.contentAs[JsonObjectSafe]
    status <- json.str("status")
  } yield status) match {
    case Success(status) => println(s"Couchbase is $status")
    case Failure(err) => println("Error: " + err)
  }
  // #end::get-for[]

  // #tag::get-map[]
  collection.get("document-key")
    .flatMap(_.contentAs[JsonObjectSafe])
    .flatMap(_.str("status")) match {
    case Success(status) => println(s"Couchbase is $status")
    case Failure(err) => println("Error: " + err)
  }
  // #end::get-map[]

  // #tag::upsert-with-options[]
  collection.upsert("document-key", json, timeout = 10.seconds) match {
    case Success(result) =>
    case Failure(err) => println("Error: " + err)
  }
  // #end::upsert-with-options[]

  def replace() {
    // #tag::replace[]
    val initial = JsonObject.create.put("status", "great")

    (for {
      _      <- collection.insert("document-key", initial)
      doc    <- collection.get("document-key")
      json   <- doc.contentAs[JsonObject]
      _      <- json.put("status", "awesome!")
      result <- collection.replace("document-key", json, cas = doc.cas)
    } yield result) match {
      case Success(status) =>
      case Failure(err: CASMismatchException) =>
        println("Could not write as another agent has concurrently modified the document")
      case Failure(err) => println("Error: " + err)
    }
    // #end::replace[]
  }

  def replaceRetry() {
    // #tag::replace-retry[]
    val initial = JsonObject.create.put("status", "great")

    // Insert some initial data
    collection.insert("document-key", json) match {
      case Success(result) =>

        // This is the get-and-replace we want to do, as a lambda
        val op = () => for {
          doc    <- collection.get("document-key")
          json   <- doc.contentAs[JsonObject]
          _      <- json.put("status", "awesome!")
          result <- collection.replace("document-key", json, cas = doc.cas)
        } yield result

        // Send our lambda to retryOnCASMismatch to take care of retrying it
        retryOnCASMismatch(op)

      case Failure(err) => println("Error: " + err)
    }

    // Try the provided operation, retrying on CASMismatchException
    def retryOnCASMismatch(operation: => Try[MutationResult]): Try[MutationResult] = {
      // Perform the operation
      val result = operation()

      result match {
        // Retry on any CASMismatchException errors
        case Failure(err: CASMismatchException) =>
          retryOnCASMismatch(operation)

        // If Success or any other Failure, return it
        case _ => result
      }
    }
    // #end::replace-retry[]
  }

  def remove() {
    // #tag::remove[]
    collection.remove("document-key") match {
      case Success(result) =>
      case Failure(err: DocumentDoesNotExistException) =>
        println("The document does not exist")
      case Failure(err) => println("Error: " + err)
    }
    // #end::remove[]
  }

  def durability() {
    // #tag::durability[]
    collection.remove("document-key", durability = Durability.Majority) match {
      case Success(result) =>
      // The mutation is available in-memory on at least a majority of replicas
      case Failure(err: DocumentDoesNotExistException) =>
        println("The document does not exist")
      case Failure(err) => println("Error: " + err)
    }
    // #end::durability[]

    // #tag::durability-observed[]
    collection.remove("document-key",
      durability = Durability.ClientVerified(ReplicateTo.Two, PersistTo.None)) match {
      case Success(result) =>
      // The mutation is available in-memory on at least two replicas
      case Failure(err: DocumentDoesNotExistException) =>
        println("The document does not exist")
      case Failure(err) => println("Error: " + err)
    }
    // #end::durability-observed[]
  }

  def expiryInsert() {
    // #tag::expiry-insert[]
    collection.insert("document-key", json, expiration = 2.hours) match {
      case Success(result) =>
      case Failure(err) => println("Error: " + err)
    }
    // #end::expiry-insert[]
  }

  def expiryGet() {
    // #tag::expiry-get[]
    collection.get("document-key", withExpiration = true) match {
      case Success(result) =>

        result.expiration match {
          case Some(expiry) => print(s"Got expiry: $expiry")
          case _ => println("Err: no expiration field")
        }

      case Failure(err) => println("Error getting document: " + err)
    }
    // #end::expiry-get[]
  }

  def expiryReplace() {
    // #tag::expiry-replace[]
    (for {
      doc    <- collection.get("document-key", withExpiration = true)
      expiry <- doc.expiration
      json   <- doc.contentAs[JsonObject]
      _      <- json.put("foo", "bar")
      result <- collection.replace("document-key", json, expiration = expiry)
    } yield result) match {
      case Success(status) =>
      case Failure(err) => println("Error: " + err)
    }
    // #end::expiry-replace[]
  }

  def expiryTouch() {
    // #tag::expiry-touch[]
    collection.getAndTouch("document-key", expiration = 4.hours) match {
      case Success(result) =>
      case Failure(err) => println("Error: " + err)
    }
    // #end::expiry-touch[]
  }

  def counterIncrement() {
    // #tag::counter-increment[]
    // Increase a counter by 1, seeding it at an initial value of 0 if it does not exist
    collection.binary.increment("document-key", delta = 1, initial = Some(0)) match {
      case Success(result) =>
        println(s"Counter now: ${result.content}")
      case Failure(err) => println("Error: " + err)
    }

    // Decrease a counter by 1, seeding it at an initial value of 10 if it does not exist
    collection.binary.decrement("document-key", delta = 1, initial = Some(10)) match {
      case Success(result) =>
        println(s"Counter now: ${result.content}")
      case Failure(err) => println("Error: " + err)
    }
    // #end::counter-increment[]
  }

  def caseClass() {

    // #tag::cc-fails[]
    val user = User("Eric Wimp", 9, Seq(Address("29 Acacia Road")))

    // Will fail to compile
    collection.insert("eric-wimp", user)
    // #end::cc-fails[]

    // #tag::cc-get[]
    (for {
      doc   <- collection.get("document-key")
      user  <- doc.contentAs[User]
    } yield result) match {
      case Success(user: User) => println(s"User: ${user})
      case Failure(err)        => println("Error: " + err)
    }
    // #end::cc-get[]

  }