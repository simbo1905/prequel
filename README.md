PrequelRx - Rx Query Extension 
==============================

This is ```Prequel 0.3.9``` + ```rxjava-scala 0.15.1``` to extend [Prequel](https://github.com/jpersson/prequel/wiki) with a pimp package ```net.noerd.preqeuel.rx``` to run the jdbc work on a background threadpool. Access to the results is through an asynchronous ```rx.lang.scala.Observable``` from [RxJava](https://github.com/Netflix/RxJava/wiki)

Given a vanilla Prequel database setup: 

```scala
import net.noerd.prequel.DatabaseConfig
import net.noerd.prequel.SQLFormatterImplicits._
import net.noerd.prequel.ResultSetRowImplicits._

case class Bicycle( id: Long, brand: String, releaseDate: DateTime )

val database = DatabaseConfig(
    driver = "org.hsqldb.jdbc.JDBCDriver",
    jdbcURL = "jdbc:hsqldb:mem:mymemdb"
)
```

You can run jdbc queries on a background threadpool and see the output via an Observable:  

```scala
  import net.noerd.prequel.rx.DatabaseConfigRx._ 

  val observable: Observable[Bicycle] = database.observable("select id, brand, release_date from bicycles", r => {
    Bicycle( r, r, r ) 
  })
  
  val subscription = observable.subscribe( (b: Bicycle) => {
    // standard rx onNext callback running on a dedicated threadpool
    println(s"$b on a background thread")
  })
```

The background threads are a threadpool exposed as ```database.jdbcThreadPool``` which is sized to match the DatabaseConfig db connection pool max size. 

Perform any writes on a future using the jdbcThreadPool to ensure that you dont block your main application threads: 

```scala
  implicit val ec = ExecutionContext.fromExecutor(database.jdbcThreadPool)

  val future = Future {
    database.transaction(tx => tx.execute("delete from observersspectable where name = ?", "test1"))
  }
```

Don't forget to shutdown the background threadpool when you are done with: 

```scala
  database.shutdown()
```

The current implementation gets Prequel to generate a ```Seq``` of results on a background thread then fires these out via an RxJava Observable.   

If you want an ```Iteratee``` rather than an ```Observable``` take a look at [Rxplay Making Iteratees And Observables Play Nice](http://bryangilbert.com/code/2013/10/22/rxPlay-making-iteratees-and-observables-play-nice/)

### Not supported

 * True cancel of the jdbc work happening on the background thread (instead its loaded into a buffer in one pass and unsusbscribing from the observer just cancels getting the data pushed to you from the buffer) 

Its very expensive to get data into a ```ResultSet``` so normal processing should not abort reading as folks should only query for what they need. Likewise running an insert/update/delete then cancelling before you get confirmation of the effect isn't an efficient usage pattern for normal processing. 

### License

PrequelRX is licensed under the [wtfpl](http://sam.zoy.org/wtfpl/).