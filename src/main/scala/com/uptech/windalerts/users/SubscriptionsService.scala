package com.uptech.windalerts.users

import cats.data.EitherT
import cats.effect.Sync
import com.softwaremill.sttp.{HttpURLConnectionBackend, sttp, _}
import com.uptech.windalerts.Repos
import com.uptech.windalerts.domain.codecs._
import com.uptech.windalerts.domain.{UnknownError, SurfsUpError, domain}
import com.uptech.windalerts.domain.domain.{AndroidReceiptValidationRequest, ApplePurchaseVerificationRequest, AppleSubscriptionPurchase}
import io.circe.optics.JsonPath.root
import io.circe.parser
import io.circe.syntax._
import io.scalaland.chimney.dsl._
import org.log4s.getLogger

class SubscriptionsService[F[_] : Sync](repos: Repos[F]) {

  def getAndroidPurchase(request: AndroidReceiptValidationRequest): EitherT[F, SurfsUpError, domain.SubscriptionPurchase] = {
    getAndroidPurchase(request.productId, request.token)
  }

  def getAndroidPurchase(productId: String, token: String): EitherT[F, SurfsUpError, domain.SubscriptionPurchase] = {
    EitherT.pure({
      repos.androidConf().purchases().subscriptions().get(ApplicationConfig.PACKAGE_NAME, productId, token).execute().into[domain.SubscriptionPurchase].enableBeanGetters
        .withFieldComputed(_.expiryTimeMillis, _.getExpiryTimeMillis.toLong)
        .withFieldComputed(_.startTimeMillis, _.getStartTimeMillis.toLong).transform
    })
  }

  def getApplePurchase(receiptData: String, password: String): EitherT[F, SurfsUpError, AppleSubscriptionPurchase] = {
    implicit val backend = HttpURLConnectionBackend()

    val json = ApplePurchaseVerificationRequest(receiptData, password, true).asJson.toString()
    val req = sttp.body(json).contentType("application/json")
      .post(uri"https://sandbox.itunes.apple.com/verifyReceipt")

    EitherT.fromEither(
      req
        .send().body
        .left.map(UnknownError(_))
        .flatMap(json=>{
          getLogger.error(s"Json from apple $json")
          parser.parse(json)
        })
        .map(root.receipt.in_app.each.json.getAll(_))
        .flatMap(_.map(p => p.as[AppleSubscriptionPurchase])
          .filter(_.isRight).maxBy(_.right.get.expires_date_ms))
        .left.map(e => UnknownError(e.getMessage))
    )
  }
}
