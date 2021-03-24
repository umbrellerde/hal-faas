import org.junit.Test

class RandomGeneratorTest {
    @Test
    fun testRandoms() {
        val test = RandomIDGenerator.next()
        print(test)
    }
}