package Models

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

object MyJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val ratingFormat: RootJsonFormat[Rating] = jsonFormat2(Rating.apply)
  implicit val broadcastFormat: RootJsonFormat[Broadcasts] = jsonFormat2(Broadcasts.apply)
  implicit val filmFormat: RootJsonFormat[Film] = jsonFormat4(Film.apply)
}

case class Film(title: String, release_year: Option[Int], ratings: Option[List[Rating]], broadcasts: Broadcasts)

object Film {

  import MyJsonProtocol._
  def fromJson(json: String): Film = {
    json.parseJson.convertTo[Film]
  }

  def toString(film: Film): String = {
    film.toJson.toString()
  }
}