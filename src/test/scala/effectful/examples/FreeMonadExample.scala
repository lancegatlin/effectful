package effectful.examples

import scalaz.{-\/, \/, \/-}
import scala.concurrent.duration._
import effectful._
import effectful.augments.{Delay, Exceptions, Par}
import effectful.cats.{Capture, Monad, NaturalTransformation}
import effectful.examples.effects.logging.free._
import effectful.examples.adapter.scalaz.writer.WriterLogger
import effectful.examples.adapter.slf4j.Slf4jLogger
import effectful.examples.effects.sql.free._
import effectful.examples.pure.uuid.impl.JavaUUIDs
import effectful.examples.mapping.sql._
import effectful.examples.pure.user.impl._
import effectful.examples.pure.user._
import effectful.examples.pure._
import effectful.examples.pure.dao.sql.impl.SqlDocDaoImpl
import effectful.examples.pure.uuid.UUIDs.UUID
import effectful.free._

object FreeMonadExample {

  type Cmd[A] = LoggerCmd[A] \/ SqlDriverCmd[A]
  type E[A] = Free[Cmd,A]

  // todo: shouldnt need this
  implicit def capture_Free[Cmd1[_]]  =
    Capture.fromApplicative[({ type F[A] = Free[Cmd1,A]})#F]

  implicit def lift_disjunction_left[Cmd1[_],Cmd2[_]] : NaturalTransformation[Cmd1,({ type C[A] = Cmd1[A] \/ Cmd2[A] })#C] =
    new NaturalTransformation[Cmd1,({ type C[A] = Cmd1[A] \/ Cmd2[A] })#C] {
      override def apply[A](c: Cmd1[A]) = -\/(c)
    }

  implicit def lift_disjunction_right[Cmd1[_],Cmd2[_]] : NaturalTransformation[Cmd2,({ type C[A] = Cmd1[A] \/ Cmd2[A] })#C] =
    new NaturalTransformation[Cmd2,({ type C[A] = Cmd1[A] \/ Cmd2[A] })#C] {
      override def apply[A](c: Cmd2[A]) = \/-(c)
    }

  implicit val uuids = new JavaUUIDs

  val sqlDriver = new FreeSqlDriver

  val tokensDao = new SqlDocDaoImpl[String,Tokens.TokenInfo,E](
    sql = sqlDriver.liftService[E],
    recordMapping = tokenInfoRecordMapping,
    metadataMapping = tokenInfoMetadataRecordMapping
  )

  val tokens = new TokensImpl[E](
    uuids = uuids.liftService[E],
    tokensDao = tokensDao,
    tokenDefaultDuration = 10.days,
    logger = FreeLogger("tokens").liftService[E]
  )

  val passwords = new PasswordsImpl[E](
    passwordMismatchDelay = 5.seconds,
    logger = FreeLogger("passwords").liftService[E]
  )


  val userDao = new SqlDocDaoImpl[UUID,UsersImpl.UserData,E](
    sql = sqlDriver.liftService[E],
    recordMapping = userDataRecordMapping,
    metadataMapping = userDataMetadataRecordMapping
  )

  val users = new UsersImpl[E](
    usersDao = userDao,
    passwords = passwords,
    logger = FreeLogger("users").liftService[E]
  )

  val userLogins = new UserLoginsImpl[E](
    users = users,
    tokens = tokens,
    passwords = passwords,
    logger = FreeLogger("userLogins").liftService[E]
  )

  // todo: generalize interpreter for any disjunction of commands
  val futInterpreter = new Interpreter[Cmd,FutureLogWriterExample.E] {
    type EE[A] = FutureLogWriterExample.E[A]
    import FutureLogWriterExample.exec_Future
    import FutureLogWriterExample.capture_LogWriter

    // todo: clean this up
    override val C = implicitly[Capture[EE]]
    override val D = implicitly[Delay[EE]]
    override val M = implicitly[Monad[EE]]
    override val P = implicitly[Par[EE]]
    override val X = implicitly[Exceptions[EE]]

    val sqlInterpreter =
      new SqlDriverCmdInterpreter[EE](
        sqlDriver = FutureLogWriterExample.sqlDriver.liftService
      )
    val logInterpreter =
      new LoggerCmdInterpreter[EE](
        // todo: memoize these
        loggerName => WriterLogger(loggerName).liftService
      )
    override def apply[A](cmd: Cmd[A]): EE[A] =
      cmd match {
        case -\/(loggingCmd) => logInterpreter(loggingCmd)
        case \/-(sqlDriverCmd) => sqlInterpreter(sqlDriverCmd)
      }
  }

  val idInterpreter = new Interpreter[Cmd,Id] {
    type EE[A] = Id[A]
    // todo: clean this up
    override val C = implicitly[Capture[EE]]
    override val D = implicitly[Delay[EE]]
    override val M = implicitly[Monad[EE]]
    override val P = implicitly[Par[EE]]
    override val X = implicitly[Exceptions[EE]]

    val sqlInterpreter =
      new SqlDriverCmdInterpreter[EE](
        sqlDriver = IdExample.sqlDriver
      )
    val logInterpreter =
      new LoggerCmdInterpreter[EE](
        // todo: memoize these
        loggerName => Slf4jLogger(loggerName)
      )
    override def apply[A](cmd: Cmd[A]): EE[A] =
      cmd match {
        case -\/(loggingCmd) => logInterpreter(loggingCmd)
        case \/-(sqlDriverCmd) => sqlInterpreter(sqlDriverCmd)
      }
  }
}
