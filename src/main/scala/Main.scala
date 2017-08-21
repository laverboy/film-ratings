import java.nio.file.Paths

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream._
import akka.stream.scaladsl._
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
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
    .runForeach {
      case (Success(response), film) =>
        println(s"Result for film: ${film.title} was successful: ${response.entity}")
        response.discardEntityBytes() // don't forget this

      case (Failure(ex), film) =>
        println(s"Uploading file ${film.title} failed with $ex")
    }

  implicit val ec = system.dispatcher
  result.onComplete(_ => system.terminate())

}

case class Film(title: String, releaseYear: Option[Int])
object Film {
  def fromJson(json: String): Film = {
    implicit val formats = DefaultFormats
    parse(json).camelizeKeys.extract[Film]
  }
}

case class Rating(Source: String, Value: String)

//def thing(): Unit = {
//  val jsonResponse = """{"Title":"My Son the Fanatic","Year":"1997","Rated":"R","Released":"07 Jan 1998","Runtime":"87 min","Genre":"Comedy, Drama, Romance","Director":"Udayan Prasad","Writer":"Hanif Kureishi (short story), Hanif Kureishi","Actors":"Om Puri, Rachel Griffiths, Akbar Kurtha, Stellan Skarsgård","Plot":"Pakistani taxi-driver Parvez and prostitute Bettina find themselves trapped in the middle when Islamic fundamentalists decide to clean up their local town.","Language":"English","Country":"UK, France","Awards":"1 win & 5 nominations.","Poster":"https://images-na.ssl-images-amazon.com/images/M/MV5BMTI2MTY1ODY2M15BMl5BanBnXkFtZTcwMjQ4NzMyMQ@@._V1_SX300.jpg","Ratings":[{"Source":"Internet Movie Database","Value":"7.0/10"},{"Source":"Rotten Tomatoes","Value":"78%"}],"Metascore":"N/A","imdbRating":"7.0","imdbVotes":"1,480","imdbID":"tt0119743","Type":"movie","DVD":"25 Jan 2000","BoxOffice":"N/A","Production":"Miramax","Website":"N/A","Response":"True"}"""
//  val json = parse(jsonResponse) \ "Ratings"
//
//  implicit val formats = DefaultFormats
//
//  println(json.children.map(_.extract[Rating]))
//}