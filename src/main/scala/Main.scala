import java.nio.file.Paths

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.stream._
import akka.stream.scaladsl._
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object Main extends App {

  def createRequest(film: Film): Future[(HttpRequest, Film)] = {
    val query = film.releaseYear match {
      case Some(year) => Query("t" -> film.title, "y" -> year.toString, "apikey" -> "41d3be47")
      case None => Query("t" -> film.title, "apikey" -> "41d3be47")
    }

    Future((HttpRequest(uri = Uri("http://www.omdbapi.com").withQuery(query)), film))
  }

  implicit val system = ActorSystem("main")
  implicit val materializer = ActorMaterializer()

  val poolClient: Flow[(HttpRequest, Film), (Try[HttpResponse], Film), NotUsed] = Http().superPool[Film]()

  val films: Source[Film, Future[IOResult]] = FileIO.fromPath(Paths.get("src/main/resources/data.json"))
    .via(JsonFraming.objectScanner(1024))
    .map(_.utf8String)
    .map(Film.fromJson)

  val result = films
    .mapAsync(1)(createRequest)
    .via(poolClient)
    .map {
      case (Success(response), film) =>
        response.entity.toStrict(5 seconds).map(_.data.decodeString("UTF-8")) -> film
      case (Failure(ex), film) =>
        println(s"Uploading file ${film.title} failed with $ex")
        Future("") -> film
    }
    .mapAsync(1)(a => a._1.map(x => Film(a._2.title, a._2.releaseYear, Some(Ratings.fromJson(x)))))
    .runForeach(println)

  implicit val ec = system.dispatcher
  result.onComplete(_ => system.terminate())

}

case class Film(title: String, releaseYear: Option[Int], ratings: Option[List[Rating]])
object Film {
  def fromJson(json: String): Film = {
    implicit val formats = DefaultFormats
    parse(json).camelizeKeys.extract[Film]
  }
}

case class Rating(Source: String, Value: String)

object Ratings {
  def fromJson(json: String): List[Rating] = {
    implicit val formats = DefaultFormats
    try {
      (parse(json) \ "Ratings").children.map(_.extract[Rating])
    } catch {
      case ex: Exception => List[Rating]()
    }
  }
}