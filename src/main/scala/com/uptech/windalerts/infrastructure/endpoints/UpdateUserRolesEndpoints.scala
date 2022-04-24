package com.uptech.windalerts.infrastructure.endpoints

import cats.effect.{ContextShift, Effect}
import cats.implicits._
import com.uptech.windalerts.core.{TokenNotFoundError, UserNotFoundError}
import com.uptech.windalerts.core.user.UserRolesService
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

class UpdateUserRolesEndpoints[F[_] : Effect](userRoles: UserRolesService[F]) extends Http4sDsl[F] {
  def endpoints(): HttpRoutes[F] =
    HttpRoutes.of[F] {
      case _@GET -> Root / "update" / "trial" =>
        val action = for {
          response <- userRoles.updateTrialUsers()
        } yield response
        action.value.flatMap {
          case Right(_) => Ok()
          case Left(UserNotFoundError(_)) => NotFound("User not found")
        }

      case _@GET -> Root / "update" / "subscribed" / "android" =>
        val action = for {
          response <- userRoles.updateSubscribedUsers()
        } yield response
        action.value.flatMap {
          case Right(_) => Ok()
          case Left(UserNotFoundError(_)) => NotFound("User not found")
          case Left(TokenNotFoundError(_)) => NotFound("Token not found")
        }

      case _@GET -> Root / "update" / "subscribed" / "apple" =>
        val action = for {
          response <- userRoles.updateSubscribedUsers()
        } yield response
        action.value.flatMap {
          case Right(_) => Ok()
          case Left(UserNotFoundError(_)) => NotFound("User not found")
          case Left(TokenNotFoundError(_)) => NotFound("Token not found")
        }
    }

}
