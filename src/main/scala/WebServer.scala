import java.nio.file.Paths

import Enrichment._
import Models.{Broadcasts, Film, Rating}
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
import scala.concurrent.duration._
import scala.io.StdIn


object MyJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val ratingFormat: RootJsonFormat[Rating] = jsonFormat2(Rating.apply)
  implicit val broadcastFormat: RootJsonFormat[Broadcasts] = jsonFormat2(Broadcasts.apply)
  implicit val filmFormat: RootJsonFormat[Film] = jsonFormat4(Film.apply)
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
        getFromDirectory("public/index.html")
      } ~
        path("data") {
          get {
            parameters('rating.as[String] ? "Metacritic") { rating =>
              complete(films(rating).run().map(a => a.sortBy(film => ratingsSorter(film, rating)).reverse))
            }
          }
        } ~
        path("generate") {
          withRequestTimeout(Duration.Inf) {
            get {
              complete {
                Enrich().result.run().map(a => "Done".toJson)
              }
            }
          }
        } ~
        getFromDirectory("public")

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

  private def ratingsSorter(f: Film, rating: String) = {
    f.ratings.get.find {
      case Rating(ratingName, _) if ratingName == rating => true
      case _ => false
    }.get.realValue()
  }

  private def films(rating: String): RunnableGraph[Future[immutable.Seq[Film]]] = FileIO.fromPath(Paths.get("output.json"))
    .via(JsonFraming.objectScanner(1024))
    .map(_.utf8String)
    .map(_.parseJson.convertTo[Film])
    .filter(_.ratings.get.exists {
      case item@Rating(ratingName, _) if ratingName == rating && item.realValue > 80 => true
      case _ => false
    })
    .toMat(Sink.seq[Film])(Keep.right)
}