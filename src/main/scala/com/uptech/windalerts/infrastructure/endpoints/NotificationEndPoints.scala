package com.uptech.windalerts.infrastructure.endpoints

import cats.effect.Effect
import com.uptech.windalerts.core.notifications.NotificationsService
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._

class NotificationEndpoints[F[_] : Effect](notifications: NotificationsService[F]) extends Http4sDsl[F] {
  def allRoutes() =
    routes().orNotFound

  def routes() = {
    import cats.syntax.flatMap._

    HttpRoutes.of[F] {
      case GET -> Root / "notify" => {
        notifications.sendNotification().flatMap {
          _ => Ok()
        }
      }


      case GET -> Root => {
        notifications.sendNotification().flatMap {
          _ => Ok()
        }
      }
    }


  }
}