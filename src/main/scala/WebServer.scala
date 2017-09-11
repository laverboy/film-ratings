import java.nio.file.Paths

import Models.{Film, Rating}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, JsonFraming, Keep, RunnableGraph, Sink}
import spray.json._

import scala.collection.immutable
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
          complete(films().run().map(a => a.sortBy(ratingsSorter).reverse))
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

  private def ratingsSorter(f: Film) = {
      f.ratings.get.find {
        case Rating("Metacritic", _) => true
        case _ => false
      }.get.realValue()
  }

  private def films(): RunnableGraph[Future[immutable.Seq[Film]]] = FileIO.fromPath(Paths.get("output.json"))
    .via(JsonFraming.objectScanner(1024))
    .map(_.utf8String)
    .map(_.parseJson.convertTo[Film])
    .filter(_.ratings.get.nonEmpty)
    .filter(_.ratings.get.exists {
      case item @ Rating("Metacritic", _) if item.realValue > 80 => true
      case _ => false
    })
    .toMat(Sink.seq[Film])(Keep.right)
}