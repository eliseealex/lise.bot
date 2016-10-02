package press.lis.lise

import java.sql.Timestamp

import akka.actor.{Actor, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging

/**
  *
  * @author eliseev
  */
object TestServer {
  def props() = Props(new TestServer())
}

class TestServer() extends Actor with StrictLogging {


  implicit val ec = context.dispatcher
  implicit val system = context.system
  implicit val materializer = ActorMaterializer()

  private val startTime: Timestamp = new Timestamp(context.system.startTime)

  override def receive: Receive = {
    case _ =>
  }

  val route =
    logRequestResult("watcher") {
      path("") {
        post {
          formFieldMap { params =>
            logger.debug(s"Obtained parameters: $params")

            complete("")
          }
        }
      } ~
        path("status") {
          get {
            complete(s"It works. Since $startTime")
          }
        }
    }

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

}

