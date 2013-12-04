package net.noerd.prequel.rx

import rx.lang.scala._
import rx.lang.scala.subscriptions._
import rx.lang.scala.concurrency._
import net.noerd.prequel._
import scala.concurrent.{ ExecutionContext, Future }
import java.util.concurrent.Executors

class DatabaseConfigObservable(val database: DatabaseConfig) {
  val threadPool = Executors.newFixedThreadPool(database.poolConfig.maxActive)
  val jdbcSchedular = Schedulers.executor(threadPool)

  def observable[T](sql: String, params: Formattable*)(block: ResultSetRow => T) = {
    @volatile var subscribed = true
    val obs = Observable((observer: Observer[T]) => {
      database.transaction { tx =>
        tx.select(sql, params: _*) { row =>
          if (subscribed) observer.onNext(block(row))
          else throw new InterruptedException("rx observable is unsubscribed")
        }
      }
      observer.onCompleted()
      BooleanSubscription {
        subscribed = false
      }
    })
    obs.subscribeOn(jdbcSchedular)
  }

  def shutdown() {
    threadPool.shutdown()
  }
}

object DatabaseConfigObservable {
  def apply(value: DatabaseConfig) = new DatabaseConfigObservable(value)
}

object DatabaseConfigRx {
  implicit def databaseConfig2observabe(wrapped: DatabaseConfig) = DatabaseConfigObservable(wrapped)
}
