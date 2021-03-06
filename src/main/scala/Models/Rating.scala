package Models

import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write

case class Rating(Source: String, Value: String) {
  def realValue(): Int = Source match {
    case "Metacritic" => Value.split("/").head.toInt
    case "Rotten Tomatoes" => Value.split("%").head.toInt
    case "Internet Movie Database" => Value.split("/").head.replace(".", "").toInt
  }
}

object Rating {
  implicit val formats = DefaultFormats
  def fromJson(json: String): List[Rating] = {
    try {
      (parse(json) \ "Ratings").children.map(_.extract[Rating])
    } catch {
      case ex: Exception => List[Rating]()
    }
  }
  def toString(rating: Rating): String = {
    write(rating)
  }
}
