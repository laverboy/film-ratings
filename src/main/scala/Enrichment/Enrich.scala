package Enrichment

import java.nio.file.Paths

import Models.MyJsonProtocol._
import Models._
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.{FileIO, Flow, JsonFraming, Keep, RunnableGraph, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class Enrich(implicit val system: ActorSystem, implicit val materializer: ActorMaterializer) {

  def createRequest(film: Film): Future[(HttpRequest, Film)] = {
    val query = film.release_year match {
      case Some(year) => Query("t" -> film.title, "y" -> year.toString, "apikey" -> "41d3be47")
      case None => Query("t" -> film.title, "apikey" -> "41d3be47")
    }

    Future((HttpRequest(uri = Uri("http://www.omdbapi.com").withQuery(query)), film))
  }

  val poolClient: Flow[(HttpRequest, Film), (Try[HttpResponse], Film), NotUsed] = Http().superPool[Film]()

  val films: Source[Film, Future[IOResult]] = FileIO.fromPath(Paths.get("src/main/resources/data.json"))
    .via(JsonFraming.objectScanner(1024))
    .map(_.utf8String)
    .map(_.parseJson.convertTo[Film])

  val result: RunnableGraph[Future[IOResult]] = films
    .mapAsync(10)(createRequest)
    .via(poolClient)
    .map {
      case (Success(response), film) =>
        println("Successful call to omdbapi")
        response.entity.toStrict(5 seconds).map(_.data.decodeString("UTF-8")) -> film
      case (Failure(ex), film) =>
        println(s"Uploading file ${film.title} failed with $ex")
        Future("") -> film
    }
    .mapAsync(10)(a => a._1.map(x => Film(a._2.title, a._2.release_year, Some(Rating.fromJson(x)), a._2.broadcasts)))
    .map(Film.toString)
    .map(s => ByteString(s + "\n"))
    .toMat(FileIO.toPath(Paths.get("output.json")))(Keep.right)
}
