package aia.faulttolerance

package dbstrategy1 {

  import java.io.File
  import java.util.UUID

  import akka.actor.SupervisorStrategy.{Restart, Resume, Stop}
  import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, AllForOneStrategy, OneForOneStrategy, PoisonPill, Props, SupervisorStrategy, Terminated}

  object LogProcessingApp extends App {
    val sources = Vector("file:///source1/", "file:///source2/")
    val system = ActorSystem("logprocessing")

    val databaseUrl = Vector(
      "http://mydatabase1",
      "http://mydatabase2",
      "http://mydatabase3"
    )

    system.actorOf(
      LogProcessingSupervisor.props(sources, databaseUrl),
      LogProcessingSupervisor.name
    )
  }

  object LogProcessingSupervisor {
    def props(sources: Vector[String], databaseUrls: Vector[String]) =
      Props(new LogProcessingSupervisor(sources, databaseUrls))

    def name = "file-watcher-supervisor"
  }

  class LogProcessingSupervisor(sources: Vector[String],
                                databaseUrls: Vector[String])
    extends Actor with ActorLogging {
    var fileWatchers: Vector[ActorRef] = sources.map {
      source =>
        val fileWatcher = context.actorOf(Props(new FileWatcher(source, databaseUrls)))
        context.watch(fileWatcher)
        fileWatcher
    }

    override def supervisorStrategy: SupervisorStrategy = AllForOneStrategy() {
      case _: DiskError => Stop
    }

    override def receive: Receive = {
      case Terminated(fileWatcher) =>
        fileWatchers = fileWatchers.filterNot(_ == fileWatcher)
        if (fileWatchers.isEmpty) {
          log.info("Shutting down, all file watchers have failed.")
          context.system.terminate()
        }
    }
  }

  object FileWatcher {

    case class NewFile(file: File, timeAdded: Long)

    case class SourceAbandoned(uri: String)

  }

  class FileWatcher(source: String,
                    databaseUrls: Vector[String])
    extends Actor with ActorLogging with FileWatchingAbilities {
    register(source)

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: CorruptedFileException => Resume
    }

    val logProcessor = context.actorOf(
      LogProcessor.props(databaseUrls),
      LogProcessor.name
    )
    context.watch(logProcessor)

    import FileWatcher._

    override def receive: Receive = {
      case NewFile(file, _) =>
        logProcessor ! LogProcessor.LogFile(file)
      case SourceAbandoned(uri) if uri == source =>
        log.info(s"$uri abandoned, stopping file watcher.")
        self ! PoisonPill
      case Terminated(`logProcessor`) =>
        log.info(s"Log processor terminated, stopping file watcher.")
        self ! PoisonPill
    }
  }

  trait FileWatchingAbilities {
    def register(uri: String): Unit = {}
  }

  object LogProcessor {

    def props(databaseUrls: Vector[String]) = Props(new LogProcessor(databaseUrls))

    def name = s"log_processor_${UUID.randomUUID().toString}"

    case class LogFile(file: File)

  }

  class LogProcessor(databaseUrls: Vector[String])
    extends Actor with ActorLogging with LogParsing {
    require(databaseUrls.nonEmpty)

    val initialDatabaseUrl = databaseUrls.head
    var alternateDatabases = databaseUrls.tail

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: DbBrokenConnectionException => Restart
      case _: DbNodeDownException => Stop
    }

    var dbWriter = context.actorOf(
      DbWriter.props(initialDatabaseUrl),
      DbWriter.name(initialDatabaseUrl)
    )
    context.watch(dbWriter)

    import LogProcessor._

    override def receive: Receive = {
      case LogFile(file) =>
        val lines = parse(file)
        lines.foreach(dbWriter ! _)
      case Terminated(_) =>
        if (alternateDatabases.nonEmpty) {
          val newDatabaseUrl = alternateDatabases.head
          alternateDatabases = alternateDatabases.tail
          dbWriter = context.actorOf(
            DbWriter.props(newDatabaseUrl),
            DbWriter.name(newDatabaseUrl)
          )
          context.watch(dbWriter)
        } else {
          log.error("All Db nodes broken, stopping.")
          self ! PoisonPill
        }
    }
  }

  trait LogParsing {

    import DbWriter._

    def parse(file: File): Vector[Line] = {
      Vector.empty[Line]
    }
  }

  object DbWriter {
    def props(databaseUrl: String) = Props(new DbWriter(databaseUrl))

    def name(databaseUrl: String) = s"""db-writer-${databaseUrl.split("/").last}"""

    case class Line(time: Long, message: String, messageType: String)

  }

  class DbWriter(databaseUrl: String) extends Actor {
    val connection = new DbCon(databaseUrl)

    import DbWriter._

    override def receive: Receive = {
      case Line(time, message, msgType) =>
        connection.write(Map('time -> time, 'message -> message, 'msgType -> msgType))
    }

    override def postStop(): Unit = {
      connection.close()
    }
  }

  class DbCon(url: String) {
    def write(map: Map[Symbol, Any]): Unit = {}

    def close(): Unit = {}
  }

  @SerialVersionUID(1L)
  class DiskError(msg: String)
    extends Error(msg) with Serializable

  @SerialVersionUID(1L)
  class CorruptedFileException(msg: String, val file: File)
    extends Exception(msg) with Serializable

  @SerialVersionUID(1L)
  class DbBrokenConnectionException(msg: String)
    extends Exception(msg) with Serializable

  @SerialVersionUID(1L)
  class DbNodeDownException(msg: String)
    extends Exception(msg) with Serializable

}
