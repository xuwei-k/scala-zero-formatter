package zeroformatter

import java.time._
import TimeFormatterInstances._
import dog._

object TimeFormatterTest extends Base {

  val `serialize and deserialize LocalDateTime` = TestCase {
    val value = LocalDateTime.now()
    val r = ZeroFormatter.serialize(value)
    assert.equal(value, ZeroFormatter.deserialize[LocalDateTime](r))
  }

  val `serialize and deserialize OffsetDateTime` = TestCase {
    val value = OffsetDateTime.now()
    val r = ZeroFormatter.serialize(value)
    assert.equal(value, ZeroFormatter.deserialize[OffsetDateTime](r))
  }

  val `serialize and deserialize ZonedDateTime` = TestCase {
    val value = OffsetDateTime.now().toZonedDateTime
    val r = ZeroFormatter.serialize(value)
    assert.equal(value, ZeroFormatter.deserialize[ZonedDateTime](r))
  }
}
