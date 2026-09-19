package kotest

import example.TestApplication
import io.github.brody0125.springtestisolation.WorkerStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

abstract class IsolatedKotestSpec : FunSpec() {
    override fun extensions() = listOf(SpringExtension)
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var store: WorkerStore
    init {
        val specName = this::class.simpleName!!
        test("isolated worker database") {
            println(
                "EVIDENCE start time=${System.currentTimeMillis()} worker=${System.getProperty("org.gradle.test.worker")}" +
                    " class=$specName context=${System.identityHashCode(jdbc.dataSource)}"
            )
            store.namespace.shouldNotBeNull()
            jdbc.update("INSERT INTO records VALUES (1, ?)", store.namespace)
            jdbc.queryForObject("SELECT value FROM records WHERE id=1", String::class.java) shouldBe store.namespace
            println(
                "EVIDENCE end time=${System.currentTimeMillis()} worker=${System.getProperty("org.gradle.test.worker")} class=$specName"
            )
        }
    }
}

@SpringBootTest(classes = [TestApplication::class])
class KotestAlphaSpec : IsolatedKotestSpec()

@SpringBootTest(classes = [TestApplication::class])
class KotestBetaSpec : IsolatedKotestSpec()

@SpringBootTest(classes = [TestApplication::class])
class KotestGammaSpec : IsolatedKotestSpec()

@SpringBootTest(classes = [TestApplication::class], properties = ["kotest.fixture.group=other"])
class KotestDeltaSpec : IsolatedKotestSpec()
