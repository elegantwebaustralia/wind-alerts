package com.uptech.windalerts.users

import cats.data._
import cats.effect.IO
import com.github.t3hnar.bcrypt._
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.model.SubscriptionPurchase
import com.restfb.{DefaultFacebookClient, Parameter, Version}
import com.uptech.windalerts.alerts.AlertsRepositoryT
import com.uptech.windalerts.domain.domain.UserType._
import com.uptech.windalerts.domain.domain._
import com.uptech.windalerts.domain.{domain, secrets}
import org.mongodb.scala.bson.ObjectId
import io.scalaland.chimney.dsl._

class UserService(userRepo: UserRepositoryAlgebra[IO],
                  credentialsRepo: CredentialsRepositoryAlgebra[IO],
                  facebookCredentialsRepo: FacebookCredentialsRepositoryAlgebra[IO],
                  alertsRepository: AlertsRepositoryT[IO],
                  facebookSecretKey: String,
                  androidPublisher: AndroidPublisher) {

  def verifyEmail(id: String) = {
    for {
      user <- getUser(id)
      operationResult <- updateUserType(user, Trial.value)
    } yield operationResult
  }

  def makeUserPremium(id: String, start:Long, expiry: Long) = {
    for {
      user <- getUser(id)
      operationResult <- updateUserType(user, Premium.value, start, expiry)
    } yield operationResult
  }

  def makeUserRegisteredExpired(id: String): EitherT[IO, ValidationError, UserT] = {
    for {
      user <- getUser(id)
      operationResult <- makeRegisteredExpired(user)
    } yield operationResult
  }

  private def makeRegisteredExpired(user: UserT): EitherT[IO, ValidationError, UserT] = {
    userRepo.update(user.copy(userType = RegisteredExpired.value, nextPaymentAt = -1)).toRight(CouldNotUpdateUserError())
  }

  private def updateUserType(user: UserT, userType: String, lastPaymentAt: Long = -1, nextPaymentAt: Long = -1): EitherT[IO, ValidationError, UserT] = {
    userRepo.update(user.copy(userType = userType, lastPaymentAt = lastPaymentAt, nextPaymentAt = nextPaymentAt)).toRight(CouldNotUpdateUserError())
  }


  def updateUserProfile(id: String, name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long): EitherT[IO, ValidationError, UserT] = {
    for {
      user <- getUser(id)
      operationResult <- updateUser(name, snoozeTill, disableAllAlerts, notificationsPerHour, user)
    } yield operationResult
  }

  private def updateUser(name: String, snoozeTill: Long, disableAllAlerts: Boolean, notificationsPerHour: Long, user: UserT): EitherT[IO, ValidationError, UserT] = {
    userRepo.update(user.copy(name = name, snoozeTill = snoozeTill, disableAllAlerts = disableAllAlerts, notificationsPerHour = notificationsPerHour, lastPaymentAt = -1, nextPaymentAt = -1)).toRight(CouldNotUpdateUserError())
  }

  def updateDeviceToken(userId: String, deviceToken: String) =
    userRepo.updateDeviceToken(userId, deviceToken)

  def updatePassword(userId: String, password: String): OptionT[IO, Unit] =
    credentialsRepo.updatePassword(userId, password.bcrypt)

  def getFacebookUserByAccessToken(accessToken: String, deviceType: String) = {
    for {
      facebookClient <- EitherT.liftF(IO(new DefaultFacebookClient(accessToken, facebookSecretKey, Version.LATEST)))
      facebookUser <- EitherT.liftF(IO(facebookClient.fetchObject("me", classOf[com.restfb.types.User], Parameter.`with`("fields", "name,id,email"))))
      dbUser <- getUser(facebookUser.getEmail, deviceType)
    } yield dbUser
  }

  def createUser(rr: FacebookRegisterRequest): EitherT[IO, UserAlreadyExistsError, (UserT, FacebookCredentialsT)] = {
    for {
      facebookClient <- EitherT.liftF(IO(new DefaultFacebookClient(rr.accessToken, facebookSecretKey, Version.LATEST)))
      facebookUser <- EitherT.liftF(IO(facebookClient.fetchObject("me", classOf[com.restfb.types.User], Parameter.`with`("fields", "name,id,email"))))
      _ <- doesNotExist(facebookUser.getEmail, rr.deviceType)

      savedCreds <- EitherT.liftF(facebookCredentialsRepo.create(FacebookCredentialsT(facebookUser.getEmail, rr.accessToken, rr.deviceType)))
      savedUser <- EitherT.liftF(userRepo.create(UserT.create(new ObjectId(savedCreds._id.toHexString), facebookUser.getEmail, facebookUser.getName, rr.deviceId, rr.deviceToken, rr.deviceType, System.currentTimeMillis(), Trial.value, -1, false, 4)))
    } yield (savedUser, savedCreds)
  }

  def createUser(rr: RegisterRequest): EitherT[IO, UserAlreadyExistsError, UserT] = {
    val credentials = Credentials(rr.email, rr.password.bcrypt, rr.deviceType)
    for {
      _ <- doesNotExist(credentials.email, credentials.deviceType)
      savedCreds <- EitherT.liftF(credentialsRepo.create(credentials))
      saved <- EitherT.liftF(userRepo.create(UserT.create(new ObjectId(savedCreds._id.toHexString), rr.email, rr.name, rr.deviceId, rr.deviceToken, rr.deviceType, System.currentTimeMillis(), Registered.value, -1, false, 4)))
    } yield saved
  }


  def doesNotExist(email: String, deviceType: String) = {
    for {
      emailDoesNotExist <- countToEither(credentialsRepo.count(email, deviceType))
      facebookDoesNotExist <- countToEither(facebookCredentialsRepo.count(email, deviceType))
    } yield (emailDoesNotExist, facebookDoesNotExist)
  }

  private def countToEither(fbCount: IO[Int]) = {
    val fbcredentialDoesNotExist: EitherT[IO, UserAlreadyExistsError, Unit] = EitherT.liftF(fbCount).flatMap(c => {
      val e: Either[UserAlreadyExistsError, Unit] = if (c > 0) Left(UserAlreadyExistsError("", ""))
      else Right(())
      EitherT(IO(e))
    })
    fbcredentialDoesNotExist
  }

  def getUserAndUpdateRole(userId: String): EitherT[IO, UserNotFoundError, UserT] = {
    for {
      eitherUser <- OptionT(userRepo.getByUserId(userId)).toRight(UserNotFoundError())
      updated <- updateRole(eitherUser)
    } yield updated
  }

  def getUserAndUpdateRole(email: String, deviceType: String): EitherT[IO, UserNotFoundError, UserT] = {
    for {
      eitherUser <- OptionT(userRepo.getByEmailAndDeviceType(email, deviceType)).toRight(UserNotFoundError())
      updated <- updateRole(eitherUser)
    } yield updated
  }

  private def updateRole(eitherUser: UserT): EitherT[IO, UserNotFoundError, UserT] = {
    if (UserType(eitherUser.userType) == Trial && eitherUser.isTrialEnded()) {
      for {
        updated <- update(eitherUser.copy(userType = UserType.TrialExpired.value, lastPaymentAt = -1, nextPaymentAt = -1))
        _ <- EitherT.liftF(alertsRepository.disableAllButOneAlerts(updated._id.toHexString))
      } yield updated
    } else {
      EitherT.fromEither(toEither(eitherUser))
    }
  }

  private def toEither(user: UserT): Either[UserNotFoundError, UserT] = {
    Right(user)
  }

  def getUser(email: String, deviceType: String): EitherT[IO, UserNotFoundError, UserT] =
    OptionT(userRepo.getByEmailAndDeviceType(email, deviceType)).toRight(UserNotFoundError())

  def getUser(userId: String): EitherT[IO, ValidationError, UserT] =
    OptionT(userRepo.getByUserId(userId)).toRight(UserNotFoundError())

  def getByCredentials(
                        email: String, password: String, deviceType: String
                      ): EitherT[IO, ValidationError, Credentials] =
    for {
      creds <- credentialsRepo.findByCreds(email, deviceType).toRight(UserAuthenticationFailedError(email))
      passwordMatched <- isPasswordMatch(password, creds)
    } yield passwordMatched


  private def isPasswordMatch(password: String, creds: Credentials): EitherT[IO, ValidationError, Credentials] = {
    val passwordMatch =
      if (password.isBcrypted(creds.password)) {
        Right(creds)
      } else {
        Left(UserAuthenticationFailedError(creds.email))
      }
    EitherT(IO(passwordMatch))
  }

  def update(user: UserT): EitherT[IO, UserNotFoundError, UserT] =
    for {
      saved <- userRepo.update(user).toRight(UserNotFoundError())
    } yield saved


  def getPurchase(request: AndroidReceiptValidationRequest): EitherT[IO, ValidationError, domain.SubscriptionPurchase] = {
    getPurchase(request.productId, request.token)
  }

  def getPurchase(productId: String, token: String): EitherT[IO, ValidationError, domain.SubscriptionPurchase] = {
    EitherT.liftF(IO({
      androidPublisher.purchases().subscriptions().get(ApplicationConfig.PACKAGE_NAME, productId, token).execute().into[domain.SubscriptionPurchase].enableBeanGetters
        .withFieldComputed(_.expiryTimeMillis, _.getExpiryTimeMillis.toLong)
        .withFieldComputed(_.startTimeMillis, _.getStartTimeMillis.toLong).transform
    }))
  }

  def updateSubscribedUserRole(user: UserId, purchase: domain.SubscriptionPurchase) = {
    if (purchase.expiryTimeMillis > System.currentTimeMillis()) {
      makeUserPremium(user.id, purchase.startTimeMillis, purchase.expiryTimeMillis)
    } else {
      makeUserRegisteredExpired(user.id)
    }
  }
}

object UserService {
  def apply[F[_]](
                   usersRepository: UserRepositoryAlgebra[IO],
                   credentialsRepository: CredentialsRepositoryAlgebra[IO],
                   facebookCredentialsRepositoryAlgebra: FacebookCredentialsRepositoryAlgebra[IO],
                   alertsRepository: AlertsRepositoryT[IO],
                   androidPublisher: AndroidPublisher
                 ): UserService =
    new UserService(usersRepository, credentialsRepository, facebookCredentialsRepositoryAlgebra, alertsRepository, secrets.read.surfsUp.facebook.key, androidPublisher)
}