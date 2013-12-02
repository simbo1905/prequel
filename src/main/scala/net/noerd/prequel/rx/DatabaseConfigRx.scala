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

  def observable[T](query: String, mapper: (ResultSetRow) => T) = {
    @volatile var subscribed = true
    val obs = Observable((observer: Observer[T]) => {
      database.transaction { tx =>
        tx.select(query) { row =>
          if (subscribed) observer.onNext(mapper(row))
          else throw new InterruptedException("rx observable is unsubscribed")
        }
      }
      observer.onCompleted()
      BooleanSubscription {
        subscribed = false
        observer.onCompleted()
        println(s"subscribed set to false and completed called")
      }
    })
    obs.observeOn(jdbcSchedular)
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
