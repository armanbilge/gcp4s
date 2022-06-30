package gcp4s.drive

import cats.syntax.all._
import io.circe.Decoder.Result
import io.circe._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

// TODO is this the right model for durations with GCP/drive? at least it will get things compiling for now
package object model {
  implicit final val finiteDurationDecoder: Decoder[FiniteDuration] =
    new Decoder[FiniteDuration] {
      def apply(c: HCursor): Result[FiniteDuration] =
        for {
          length <- c.downField("length").as[Long]
          unitString <- c.downField("unit").as[String]
          unit <- Either.catchOnly[IllegalArgumentException](TimeUnit.valueOf(unitString)).leftMap(DecodingFailure.fromThrowable(_, c.history))
        } yield FiniteDuration(length, unit)
    }

  implicit final val finiteDurationEncoder: Encoder[FiniteDuration] = new Encoder[FiniteDuration] {
    final def apply(a: FiniteDuration): Json =
      Json.obj(
        "length" -> Json.fromLong(a.length),
        "unit"   -> Json.fromString(a.unit.name),
      )
  }
}
