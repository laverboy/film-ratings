package Enrichment

import java.io.File

import Models.{Broadcasts, Film, Rating}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source

class EnrichTest extends TestKit(ActorSystem("MySpec"))
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  val testOutputFile = "small-output.json"
  val testInputFile = "src/main/resources/small.json"

  override def afterAll {
    val fileTemp = new File(testOutputFile)
    if (fileTemp.exists) {
      fileTemp.delete()
    }
    TestKit.shutdownActorSystem(system)
  }

  implicit val materializer = ActorMaterializer()
  "Enrich" must {

    "parse json into films object" in {
      val future = Enrich().films(testInputFile).runWith(Sink.seq)
      val result = Await.result(future, 3.seconds)

      assert(result.size == 5)
      assert(result.exists {
        case Film("This Boy's Life", Some(1993), None, Broadcasts(None, Some("9f83e318-b3ce-4722-a386-b992e43062dd"))) => true
        case _ => false
      })
    }

    "enrich films by calling omdbapi" in {
      val future = Enrich().callOmdb(testInputFile).runWith(Sink.seq[Film])
      val result = Await.result(future, 3.seconds)

      val ratings = Some(List(Rating("Internet Movie Database","7.3/10"), Rating("Rotten Tomatoes","75%"), Rating("Metacritic","60/100")))
      assert(result.size == 5)
      assert(result.exists {
        case Film("This Boy's Life", Some(1993), `ratings`, Broadcasts(None, Some("9f83e318-b3ce-4722-a386-b992e43062dd"))) => true
        case _ => false
      })
    }

    "write completed enrichment items to file" in {
      val future = Enrich().result(testInputFile, testOutputFile).run()
      val result = Await.result(future, 3.seconds)

      val output = Source.fromFile(testOutputFile).mkString
      assert(output.contains("""{"title":"I Went Down","ratings":[{"Source":"Internet Movie Database","Value":"7.0/10"},"""
      + """{"Source":"Rotten Tomatoes","Value":"84%"},{"Source":"Metacritic","Value":"69/100"}],"""
      + """"broadcasts":{"sd":"145cb493-e7be-4e1f-8d9b-f29e815ba705"}}"""))

    }
  }

}
