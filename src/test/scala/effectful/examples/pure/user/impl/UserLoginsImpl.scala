package effectful.examples.pure.user.impl

import effectful.cats.Monad
import effectful.examples.effects.logging.Logger
import effectful.examples.pure.user._

import scalaz.{-\/, \/, \/-}

class UserLoginsImpl[E[_]](
  logger: Logger[E],
  users: Users[E],
  passwords: Passwords[E],
  tokens: Tokens[E]
)(implicit
  E:Monad[E]
) extends UserLogins[E] {
  import Monad.ops._
  import UserLogins._
  import logger._

  override def login(username: String, password: String): E[LoginFailure \/ Token] =
    for {
      maybeUser <- users.findByUsername(username)
      result <- maybeUser match {
        case Some(user:Users.User) =>
          for {
            digest <- passwords.mkDigest(password)
            passwordOk <- passwords.compareDigest(user.passwordDigest,digest)
            result <- {
              if(passwordOk) {
                for {
                  tuple <- tokens.issue(
                    userId = user.id,
                    deviceId = None,
                    expireAfter = None
                  )
                  (token,_) = tuple
                  _ <- info(s"User ${user.id} logged in")
                } yield \/-(token)
              } else {
                for {
                  _ <- warn(s"User ${user.id} password mismatch")
                } yield -\/(LoginFailure.PasswordMismatch)
              }
            }:E[LoginFailure \/ Token]
          } yield result
        case None =>
          E(-\/(LoginFailure.UserDoesNotExist))
      }
    } yield result
}