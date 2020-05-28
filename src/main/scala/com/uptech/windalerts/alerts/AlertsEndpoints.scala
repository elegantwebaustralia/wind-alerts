package com.uptech.windalerts.alerts

import cats.data.{EitherT, OptionT}
import cats.effect.IO
import com.uptech.windalerts.domain.{HttpErrorHandler, ValidationError}
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.users.{Auth, UserService}
import io.scalaland.chimney.dsl._
import org.http4s.AuthedRoutes
import org.http4s.dsl.Http4sDsl


class AlertsEndpoints(alertService: AlertsService[IO], usersService: UserService[IO], auth: Auth, httpErrorHandler: HttpErrorHandler[IO]) extends Http4sDsl[IO] {
  def allUsersService(): AuthedRoutes[UserId, IO] =
    AuthedRoutes {
      case GET -> Root as user => {
        val action = for {
          resp <- EitherT.liftF(alertService.getAllForUser(user.id))
            .map(alerts=>Alerts(alerts.alerts.map(a => a.into[Alert].withFieldComputed(_.id, u => u._id.toHexString).transform)))
        } yield resp
        OptionT.liftF({
          action.value.flatMap {
            case Right(reponse) => Ok(reponse)
            case Left(error) => httpErrorHandler.handleThrowable(error)
          }
        })
      }

      case _@DELETE -> Root / alertId as user => {
        val action = for {
          eitherDeleted <- alertService.deleteT(user.id, alertId)
        } yield eitherDeleted
        OptionT.liftF({
          action.value.flatMap {
            case Right(_) => NoContent()
            case Left(error) => httpErrorHandler.handleThrowable(new RuntimeException(error))
          }
        })
      }


      case authReq@PUT -> Root / alertId as user => {

        val response = authReq.req.decode[AlertRequest] { alert =>
          val action = for {
            dbUser <- usersService.getUser(user.id)
            _ <- auth.authorizePremiumUsers(dbUser)
            updated <- alertService.updateT(user.id, alertId, alert)
          } yield updated

          action.value.flatMap {
            case Right(value) => Ok(value.into[Alert].withFieldComputed(_.id, u => u._id.toHexString).transform)
            case Left(error) => httpErrorHandler.handleError(error)
          }
        }
        OptionT.liftF(response)
      }

      case authReq@POST -> Root as user => {
        val response = authReq.req.decode[AlertRequest] { alert =>
          val action = for {
            dbUser <- usersService.getUser(user.id)
            _ <- auth.authorizePremiumUsers(dbUser)
            saved <- EitherT.liftF(alertService.save(alert, user.id)).asInstanceOf[EitherT[IO, ValidationError, AlertT]]
          } yield saved

          action.value.flatMap {
            case Right(response) => Created(response.into[Alert].withFieldComputed(_.id, u => u._id.toHexString).transform)
            case Left(error) => httpErrorHandler.handleError(error)
          }
        }
        OptionT.liftF(response)
      }
    }
}
