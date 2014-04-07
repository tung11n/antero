package antero.http

import akka.actor._
import akka.pattern.ask
import akka.io.{Tcp, IO}
import antero.store.UserDetails
import antero.system._
import antero.system.Acknowledge
import antero.system.Config
import antero.system.User
import java.net.InetSocketAddress
import java.net.InetAddress
import spray.can.Http
import spray.http.HttpMethods._
import spray.routing._
import spray.httpx.Json4sSupport
import org.json4s.{DefaultFormats, Formats}
import spray.http.HttpRequest
import spray.http.HttpResponse
import akka.actor.Terminated
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import akka.event.LoggingAdapter

/**
 * Created by tungtt on 3/22/14.
 */

class HttpService extends Actor with ActorLogging {
  import context.system

  private var configStore: ConfigStore = _
  private var store: ActorRef = _

  override def receive: Receive = {
    case Config(configStore) =>
      this.configStore = configStore
      this.store = configStore.components.getOrElse("store", sender)

      val host = configStore.getStringSetting("httpService.host", InetAddress.getLocalHost.getHostAddress)
      val port = configStore.getIntSetting("httpService.port", 8080)
      IO(Http) ! Http.Bind(self, host, port)

      sender ! Acknowledge("httpService")

    case Http.Connected(remote, _) =>
      log.info("Remote address {} connected", remote)
      sender ! Http.Register(context.actorOf(Props(classOf[RequestHandler], store, log)))
  }
}

class RequestHandler(val store: ActorRef, val log: LoggingAdapter) extends HttpServiceActor with Json4sSupport {
  implicit def json4sFormats: Formats = DefaultFormats
  implicit val timeout = Timeout(5, TimeUnit.SECONDS)
  import context.dispatcher

  def receive: Actor.Receive = runRoute {
    pathPrefix("api") {
      pathPrefix("trigger") {
        path("list"/IntNumber) {id=>
          get {
            complete("pong" + id * 2)
          }
        }
      } ~
      /*
      pathPrefix("channel") {
        path(Segment) { channel =>
          complete(new User((channel)))
        }
      } ~
      */
      path("user" / Segment) { userName =>
        get {
          log.info("Getting user info for user " + userName)
          complete(ask(store, Retrieve(UserDetails(userName))).mapTo[User])
        }
      }
    }
  }
}

class EchoConnectionHandler(remote: InetSocketAddress, connection: ActorRef) extends Actor with ActorLogging {

  context.watch(connection) // We want to know when the connection dies without sending a `Tcp.ConnectionClosed`

  def receive: Receive = {
    case HttpRequest(GET, uri, _, _, _) =>
      sender ! HttpResponse(entity = uri.path.toString + "HAHA")
    case _: Tcp.ConnectionClosed =>
      log.info("Stopping, because connection for remote address {} closed", remote)
      context.stop(self)
    case Terminated(`connection`) =>
      log.info("Stopping, because connection for remote address {} died", remote)
      context.stop(self)
  }
}