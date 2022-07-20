import com.devsisters.sharding.{ Config, GrpcPods, Server, ShardManager, StorageRedis }
import com.devsisters.sharding.interfaces.PodsHealth
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.pubsub.PubSub
import zio._
import zio.interop.catz._

object ExampleApp extends ZIOAppDefault {
  private val redis =
    ZLayer.scopedEnvironment {
      implicit val runtime: zio.Runtime[Any] = zio.Runtime.default
      implicit val logger: Log[Task]         = new Log[Task] {
        override def debug(msg: => String): Task[Unit] = ZIO.unit
        override def error(msg: => String): Task[Unit] = ZIO.logError(msg)
        override def info(msg: => String): Task[Unit]  = ZIO.logInfo(msg)
      }

      (for {
        client   <- RedisClient[Task].from("redis://localhost")
        commands <- Redis[Task].fromClient(client, RedisCodec.Utf8)
        pubSub   <- PubSub.mkPubSubConnection[Task, String, String](client, RedisCodec.Utf8)
      } yield ZEnvironment(commands, pubSub)).toScopedZIO
    }

  private val config = ZLayer.succeed(Config(300, 8080))

  def run: Task[Nothing] =
    Server.run.provide(
      config,
      redis,
      PodsHealth.local,
      GrpcPods.live,
      StorageRedis.live,
      ShardManager.live
    )
}
