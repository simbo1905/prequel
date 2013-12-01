package net.noerd.prequel.rx

import java.sql.SQLException
import scala.collection.mutable.ArrayBuffer
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.BeforeAndAfterEach
import net.noerd.prequel.SQLFormatterImplicits._
import net.noerd.prequel.ResultSetRowImplicits._
import net.noerd.prequel.rx.DatabaseConfigRx._
import net.noerd.prequel.TestDatabase
import scala.concurrent.Await
import scala.language.postfixOps
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import org.scalatest.Ignore

@Ignore
class ObservableSpec extends FunSpec with ShouldMatchers with BeforeAndAfterEach {

  import java.util.concurrent.Executors
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  val database = TestDatabase.config

  override def beforeEach() = database.transaction { tx =>
    tx.execute("create table observersspectable(id int, name varchar(265))")
    tx.execute("insert into observersspectable values(?, ?)", 242, "test1")
    tx.execute("insert into observersspectable values(?, ?)", 23, "test2")
    tx.execute("insert into observersspectable values(?, ?)", 42, "test3")
  }

  override def afterEach() = database.transaction { tx =>
    tx.execute("drop table observersspectable")
  }

  case class ObserverSpecData(id: Int, name: String, threadName: String)

  describe("Observe") {

    describe("select should return an observable") {

      it("should return an observable of the values converted by the block") {

        println(s"test thread: ${Thread.currentThread().getName()}")

        Future {
          println(s"background thread: ${Thread.currentThread().getName()}")
        }

        val futureOfObservable = database.observable("select id, name from observersspectable", r => {
          ObserverSpecData(r, r, Thread.currentThread().getName())
        })

        Await.ready(futureOfObservable, 500 milliseconds)

        val observed = futureOfObservable.value.get.get.toBlockingObservable.toList

        observed should equal(List(ObserverSpecData(242, "test1", "pool-1-thread-1"), ObserverSpecData(23, "test2", "pool-1-thread-1"), ObserverSpecData(42, "test3", "pool-1-thread-1")))
      }

    }

  }
}