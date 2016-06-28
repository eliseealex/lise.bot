package press.lis.lise.model

import com.typesafe.scalalogging.StrictLogging
import slick.driver.PostgresDriver.api._

/**
  * @author Aleksandr Eliseev
  */
object Drop extends App with StrictLogging {
  val db = Database.forConfig("lisedb")

  try {
    db.run(Model.schema.drop)
  } finally db.close()

}
