package com.uptech.windalerts.status

import cats.data.EitherT
import cats.effect.Sync
import cats.{Applicative, Functor}
import com.softwaremill.sttp.{HttpURLConnectionBackend, _}
import com.uptech.windalerts.domain.{UnknownError, domain}
import com.uptech.windalerts.domain.domain.{BeachId, SurfsUpEitherT}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, parser}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}


case class Wind(
                 speed: Double,
                 gustSpeed: Double,
                 trend: Double,
                 direction: Double,
                 directionText: String
               )

object Wind {
  implicit val windDecoder: Decoder[Wind] = deriveDecoder[Wind]

  implicit def windEntityDecoder[F[_] : Sync]: EntityDecoder[F, Wind] =
    jsonOf

  implicit val windEncoder: Encoder[Wind] = deriveEncoder[Wind]

  implicit def windEntityEncoder[F[_] : Applicative]: EntityEncoder[F, Wind] =
    jsonEncoderOf
}

class WindsService[F[_] : Sync](apiKey: String)(implicit backend: SttpBackend[Id, Nothing]) {
  def get(beachId: BeachId)(implicit F: Functor[F]): SurfsUpEitherT[F, domain.Wind] =
    EitherT.fromEither(getFromWillyWeatther(apiKey, beachId))

  def getFromWillyWeatther(apiKey: String, beachId: BeachId): Either[UnknownError, domain.Wind] = {
    for {
      body <- sttp.get(uri"https://api.willyweather.com.au/v2/$apiKey/locations/${beachId.id}/weather.json?observational=true").send()
        .body
        .left
        .map(UnknownError(_))
      parsed <- parser.parse(body).left.map(f=>UnknownError(f.message))
      wind <- parsed.hcursor.downField("observational").downField("observations").downField("wind").as[Wind].left.map(f=>UnknownError(f.message))
    } yield  domain.Wind(wind.direction, wind.speed, wind.directionText)
  }

}

