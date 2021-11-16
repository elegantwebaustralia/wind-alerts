package com.uptech.windalerts.core.social.subscriptions

trait SocialSubscription[F[_]] {
  def getPurchase(token: String): F[SubscriptionPurchase]
}
