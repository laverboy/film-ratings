import java.nio.file.Paths

import Models.{Film, Rating}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{FileIO, JsonFraming, Source}
import akka.stream.{ActorMaterializer, IOResult}
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.StdIn


object MyJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val ratingFormat: RootJsonFormat[Rating] = jsonFormat2(Rating.apply)
  implicit val filmFormat: RootJsonFormat[Film] = jsonFormat3(Film.apply)
}

object WebServer {
  import MyJsonProtocol._
  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()

  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val route =
      pathSingleSlash {
        get {
          val films: Source[Film, Future[IOResult]] = FileIO.fromPath(Paths.get("small-output.json"))
            .via(JsonFraming.objectScanner(1024))
            .map(_.utf8String)
            .map(_.parseJson.convertTo[Film])

          complete(films)
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}