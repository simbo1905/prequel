package net.noerd.prequel.rx

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

import scala.collection.mutable.ListBuffer
import java.util.concurrent.CountDownLatch

import org.scalatest.BeforeAndAfterEach
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

import net.noerd.prequel.ResultSetRowImplicits._
import net.noerd.prequel.SQLFormatterImplicits._
import net.noerd.prequel.TestDatabase
import net.noerd.prequel.rx.DatabaseConfigRx._

import _root_.rx.lang.scala.{ Observer, Observable, Scheduler, Subscription }

class ObservableSpec extends FunSpec with ShouldMatchers with BeforeAndAfterEach {

  val database = TestDatabase.config

  override def beforeEach() = database.transaction { tx =>
    tx.execute("create table observersspectable(id int, name varchar(265))")
    tx.execute("insert into observersspectable values(?, ?)", 242, "test1")
    tx.execute("insert into observersspectable values(?, ?)", 23, "test2")
    tx.execute("insert into observersspectable values(?, ?)", 42, "test3")
  }

  case class ObserverSpecData(id: Int, name: String, threadName: String)

  def assertObserverSpecData(list: Seq[ObserverSpecData], index: Int, id: Int, name: String, wrongThreadName: String) {
    list(index) match {
      case ObserverSpecData(_id, _name, x) if _id == id && _name == name && x != wrongThreadName =>
    }
  }

  override def afterEach() = {
    database.transaction { tx =>
      tx.execute("drop table observersspectable")
    }
    database.shutdown()
  }

  describe("observable extention method") {

    it("should allow you to query the database") {
      val observable = database.observable("select id, name from observersspectable") { r =>
        val t: (Int, String) = (r, r)
        t
      }

      observable.toBlockingObservable.toList should equal(List((242, "test1"), (23, "test2"), (42, "test3")))
    }

    it("should allow you to query the database with parameters") {
      val observable = database.observable("select id, name from observersspectable where name = ?", "test1") { r =>
        val t: (Int, String) = (r, r)
        t
      }

      observable.toBlockingObservable.toList should equal(List((242, "test1")))
    }

    it("should allow the subscription to be cancelled") {

      val doUnsubscribeLatch = new CountDownLatch(1)

      val observable = database.observable("select id, name from observersspectable") { r =>
        val t: (Int, String) = (r, r)
        t
      }

      val result = new ListBuffer[(Int, String)]
      @volatile var completed = false

      val subscription = observable.subscribe(n => {
        result += n
        doUnsubscribeLatch.countDown()
        Thread.sleep(100)
      }, e => {
        println("setting completed on error")
        completed = true
      }, () => {
        println("setting completed on complete")
        completed = true
      })

      doUnsubscribeLatch.await()

      subscription.unsubscribe()

      while (!completed) {
        Thread.sleep(100)
      }

      result.size should equal(1)

      result should equal(List((242, "test1")))

    }

    it("should not process any work on the thread which creates the observable") {

      var foregroundThreadName = Thread.currentThread().getName()

      val observable = database.observable("select id, name from observersspectable") { r =>
        ObserverSpecData(r, r, Thread.currentThread().getName())
      }

      val result = new ListBuffer[ObserverSpecData]

      observable.subscribe(n => {
        result.synchronized {
          result += n
          result.notify()
        }
      })

      var size = 0
      while (size < 3) {
        result.synchronized {
          result.wait(500)
          size = result.size
        }
      }

      result.size should equal(3)

      assertObserverSpecData(result, 0, 242, "test1", foregroundThreadName)
      assertObserverSpecData(result, 1, 23, "test2", foregroundThreadName)
      assertObserverSpecData(result, 2, 42, "test3", foregroundThreadName)
    }

  }

}