package Models

import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods.parse

case class Rating(Source: String, Value: String)

object Rating {
  def fromJson(json: String): List[Rating] = {
    implicit val formats = DefaultFormats
    try {
      (parse(json) \ "Ratings").children.map(_.extract[Rating])
    } catch {
      case ex: Exception => List[Rating]()
    }
  }
}
