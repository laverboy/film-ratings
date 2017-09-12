import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

class WebServerTest extends TestKit(ActorSystem("MySpec"))
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  implicit val materializer = ActorMaterializer()

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Web Server" must {
    "parse source file into sorted set" in {
      val future = WebServer.films("Metacritic", 90, getClass.getResource("/enriched-data-source.json").getPath).run()
      val result = Await.result(future, 3.seconds)

      assert(result.size == 1)

      val future2 = WebServer.films("Internet Movie Database", 70, getClass.getResource("/enriched-data-source.json").getPath).run()
      val result2 = Await.result(future2, 5.seconds)

      assert(result2.size == 2)
    }
  }

}
