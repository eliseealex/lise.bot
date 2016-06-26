package press.lis.lise.model

import com.typesafe.scalalogging.StrictLogging
import slick.driver.PostgresDriver.api._

/**
  * @author Aleksandr Eliseev
  */
object Init extends App with StrictLogging {
  val db = Database.forConfig("lisedb")

  try {
    db.run(Model.schema.create)
  } finally db.close()

}
