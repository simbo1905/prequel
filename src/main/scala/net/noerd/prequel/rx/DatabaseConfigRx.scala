package net.noerd.prequel.rx

import rx.lang.scala._
import rx.lang.scala.subscriptions._
import net.noerd.prequel._
import scala.concurrent.{ ExecutionContext, Future }

class DatabaseConfigObservable(val database: DatabaseConfig)(implicit ec: ExecutionContext) {
  def observable[T](query: String, mapper: (ResultSetRow) => T) = {
    Future {
      println(s"inner future thread: ${Thread.currentThread().getName()}")
      Observable((observer: Observer[T]) => {
        database.transaction { tx =>
          tx.select(query) { row =>
            observer.onNext(mapper(row))
          }
        }
        observer.onCompleted()
        // TODO implement unsubscribe of the work
        Subscription {}
      })
    }
  }
}

object DatabaseConfigObservable {
  def apply(value: DatabaseConfig)(implicit ec: ExecutionContext) = new DatabaseConfigObservable(value)
}

object DatabaseConfigRx {
  implicit def databaseConfig2observabe(wrapped: DatabaseConfig)(implicit ec: ExecutionContext) = DatabaseConfigObservable(wrapped)
}
