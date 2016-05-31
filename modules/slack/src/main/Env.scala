package lila.slack

import akka.actor._
import com.typesafe.config.Config

import lila.common.PimpedConfig._
import lila.hub.actorApi.slack.Event
import lila.hub.actorApi.{ DonationEvent, Deploy, RemindDeployPre, RemindDeployPost }

final class Env(
    config: Config,
    getLightUser: String => Option[lila.common.LightUser],
    system: ActorSystem) {

  private val IncomingUrl = config getString "incoming.url"
  private val IncomingDefaultChannel = config getString "incoming.default_channel"
  private val NetDomain = config getString "domain"

  lazy val api = new SlackApi(client, getLightUser)

  private lazy val client = new SlackClient(
    url = IncomingUrl,
    defaultChannel = IncomingDefaultChannel)

  private val prod = NetDomain == "lichess.org"

  system.lilaBus.subscribe(system.actorOf(Props(new Actor {
    def receive = {
      case d: DonationEvent                    => api donation d
      case Deploy(RemindDeployPre, _) if prod  => api.deployPreProd
      case Deploy(RemindDeployPost, _) if prod => api.deployPostProd
      case Deploy(RemindDeployPre, _)          => api.deployPreStage
      case Deploy(RemindDeployPost, _)         => api.deployPostStage
      case e: Event                            => api publishEvent e
    }
  })), 'donation, 'deploy, 'slack)
}

object Env {

  lazy val current: Env = "slack" boot new Env(
    system = lila.common.PlayApp.system,
    getLightUser = lila.user.Env.current.lightUser,
    config = lila.common.PlayApp loadConfig "slack")
}
