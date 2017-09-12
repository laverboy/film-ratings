import java.nio.file.Paths

import Enrichment.Enrich
import Models.{Broadcasts, Film, Rating}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, JsonFraming, Keep, RunnableGraph, Sink}
import spray.json._

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
            parameters('rating.as[String] ? "Metacritic", 'threshold.as[Int] ? 80) { (critic, threshold) =>
              complete(films(critic, threshold, "output.json").run().map(_.sortBy(film => getCriticRating(film, critic)).reverse))
            }
          }
        } ~
        path("generate") {
          withRequestTimeout(2.minutes) {
            get {
              complete {
                Enrich().result("src/main/resources/data.json", "output.json").run().map(a => "Done".toJson)
              }
            }
          }
        } ~
        getFromDirectory("public") /* Default - if not caught by other routes */

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

  private def getCriticRating(f: Film, critic: String): Option[Int] =
    for {
      ratings <- f.ratings
      maybeScore <- ratings.find {
        case Rating(`critic`, _) => true
        case _ => false
      }
      score = maybeScore.realValue()
    } yield score


  def films(rating: String, threshold: Int, source: String): RunnableGraph[Future[Seq[Film]]] =
    FileIO.fromPath(Paths.get(source))
    .via(JsonFraming.objectScanner(1024))
    .map(_.utf8String)
    .map(_.parseJson.convertTo[Film])
    .filter(hasCriticRatingThreshold(_, rating, threshold))
    .toMat(Sink.seq[Film])(Keep.right)

  private def hasCriticRatingThreshold(film: Film, critic: String, threshold: Int): Boolean = {
    getCriticRating(film, critic) match {
      case Some(score: Int) => if (score > threshold) true else false
      case None => false
    }
  }
}