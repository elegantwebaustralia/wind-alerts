package com.uptech.windalerts.core.user

import cats.data.{EitherT, OptionT}
import com.uptech.windalerts.core.UserNotFoundError

trait UserRepository[F[_]] {
  def getByUserId(userId: String): OptionT[F, UserT]

  def getByEmailAndDeviceType(email: String, deviceType: String): OptionT[F, UserT]

  def create(user: UserT): F[UserT]

  def update(user: UserT): OptionT[F, UserT]

  def findTrialExpiredUsers(): F[Seq[UserT]]

  def findAndroidPremiumExpiredUsers(): F[Seq[UserT]]

  def findApplePremiumExpiredUsers(): F[Seq[UserT]]

  def usersWithNotificationsEnabledAndNotSnoozed(): F[Seq[UserT]]
}
