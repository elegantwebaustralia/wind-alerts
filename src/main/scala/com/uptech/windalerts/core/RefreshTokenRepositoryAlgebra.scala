package com.uptech.windalerts.core

import cats.data.{EitherT, OptionT}
import com.uptech.windalerts.domain.TokenNotFoundError
import com.uptech.windalerts.domain.domain.RefreshToken

trait RefreshTokenRepositoryAlgebra[F[_]] {
  def getByAccessTokenId(accessTokenId: String): OptionT[F, RefreshToken]

  def create(refreshToken: RefreshToken): F[RefreshToken]

  def getByRefreshToken(refreshToken: String): OptionT[F, RefreshToken]

  def deleteForUserId(uid: String): F[Unit]

  def updateExpiry(id: String, expiry: Long): EitherT[F, TokenNotFoundError, RefreshToken]
}