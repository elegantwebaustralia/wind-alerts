package com.uptech.windalerts.core.otp

import cats.Monad
import cats.effect.Sync
import cats.implicits._

import scala.util.Random

class OTPService[F[_] : Sync](otpRepository: OtpRepository[F], otpNotifier: OTPNotifier[F]) {

  def send(userId: String, email: String)(implicit M: Monad[F]): F[String] = {
    val otp = createOtp(4)
    for {
      _ <- otpRepository.updateForUser(userId, otp, System.currentTimeMillis() + 5 * 60 * 1000)
      result <- otpNotifier.notifyOTP(email, otp)
    } yield result
  }

  def createOtp(n: Int) = {
    val alpha = "0123456789"
    val size = alpha.size

    (1 to n).map(_ => alpha(Random.nextInt.abs % size)).mkString
  }

}
