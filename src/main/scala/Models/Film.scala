package Models

import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

case class Film(title: String, releaseYear: Option[Int], ratings: Option[List[Rating]])

object Film {
  implicit val formats = DefaultFormats
  def fromJson(json: String): Film = {
    parse(json).camelizeKeys.extract[Film]
  }
  def toString(film: Film): String = {
    write(film)
  }
}